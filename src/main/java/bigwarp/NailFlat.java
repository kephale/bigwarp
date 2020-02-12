/**
 * License: GPL
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package bigwarp;

import static bigwarp.BigWarp.makeFlatAndOriginalSource;
import static bigwarp.BigWarp.zRange;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.imglib2.RealRandomAccessible;
import org.janelia.saalfeldlab.hotknife.FlattenTransform;
import org.janelia.saalfeldlab.hotknife.util.Show;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.ij.util.ProgressWriterIJ;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.RandomAccessibleIntervalSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.RealTransformSequence;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale2D;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/**
 *
 *
 * @author Kyle Harrington &lt;janelia@kyleharrington.com&gt;
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class NailFlat implements Callable<Void> {

	@Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i /nrs/flyem/tmp/VNC.n5")
	private String n5Path = "/nrs/flyem/alignment/kyle/nail_test.n5";

	@Option(names = {"-d", "--dataset"}, required = true, description = "Input dataset -d '/zcorr/Sec22___20200106_083252'")
	private String inputDataset = "/volumes/input";

	@Option(names = {"-s", "--cost"}, required = true, description = "Cost dataset -d '/cost/Sec22___20200110_133809/s7'")
	private String costDataset = "/volumes/cost";

	@Option(names = {"-f", "--flatten"}, required = true, description = "Flatten subcontainer -f '/flatten/Sec22___20200110_133809'")
	private String flattenDataset = "/flatten";

	@Option(names = {"--heightmaps"}, required = true, description = "Dataset for the heightmap (should contain /min and /max) -f '/flatten/Sec22___20200110_133809/heightmaps'")
	private String heightmapDataset = null;

	private boolean useVolatile = true;
	private long padding = 2000;

	FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", 1, 1, 1);

	public static final void main(String... args) {
		if( args.length == 0 )
			args = new String[]{
			        "-i", "/nrs/flyem/tmp/VNC.n5",
					"-d", "/zcorr/Sec02___20200114_103029",
					"-f", "/heightfields/Sec02_20200206_152322_kyle01",
					"-s", "/cost/Sec02_20200206_152322/s1",
					"--heightmaps", "/heightfields/Sec02_20200206_152322/s7"
					};

		CommandLine.call(new NailFlat(), args);
	}

	@Override
	public final Void call() throws Exception {
		net.imagej.ImageJ imagej = new net.imagej.ImageJ();
		final N5FSReader n5 = new N5FSReader(n5Path);

		// Extract metadata from input
		final int numScales = n5.list(inputDataset).length;
		final long[] dimensions = n5.getDatasetAttributes(inputDataset + "/s0").getDimensions();

		RandomAccessibleInterval<FloatType> min = null;
		RandomAccessibleInterval<FloatType> max = null;

		ExecutorService exec = Executors.newFixedThreadPool(8);

		System.out.println("Start time: " + LocalDateTime.now());

		double[] costDSFactors;
		double[] downsamplingFactors;
		double minMean, maxMean;

		// Min heightmap: Load from N5 if possible
		if( n5.exists(heightmapDataset) ) {
			System.out.println("Loading heightmaps from N5 " + heightmapDataset);
			min = N5Utils.open(n5, heightmapDataset + "/min");
			max = N5Utils.open(n5, heightmapDataset + "/max");
			minMean = n5.getAttribute(heightmapDataset + "/min", "avg", double.class);
			maxMean = n5.getAttribute(heightmapDataset + "/max", "avg", double.class);

			//double[] unPermutedDownsamplingFactors = n5.getAttribute(heightmapDataset, "downsamplingFactors", double[].class);
			//downsamplingFactors = new double[]{unPermutedDownsamplingFactors[0], unPermutedDownsamplingFactors[2], unPermutedDownsamplingFactors[1]};
			downsamplingFactors = n5.getAttribute(heightmapDataset, "downsamplingFactors", double[].class);
		} else {
		    System.out.println("Heightmaps are missing: " + heightmapDataset );
		    throw new Exception("Missing heightmap");
        }
		System.out.println("Time: " + LocalDateTime.now());

		System.out.println("Heightmap downsampling factors: " + downsamplingFactors[0] + " " + downsamplingFactors[1] + " " + downsamplingFactors[2]);
		System.out.println("Min heightmap: " + min.dimension(0) + " " + min.dimension(1) + " " + min.dimension(2));
		System.out.println("Max heightmap: " + max.dimension(0) + " " + max.dimension(1) + " " + max.dimension(2));

		// Setup the flatten dataset
		N5FSWriter n5w = new N5FSWriter(n5Path);
		N5Utils.save(min, n5w, flattenDataset + "/min", new int[]{1024, 1024}, new RawCompression());
		n5w.setAttribute(flattenDataset + "/min", "avg", minMean);

		N5Utils.save(max, n5w, flattenDataset + "/max", new int[]{1024, 1024}, new RawCompression());
		n5w.setAttribute(flattenDataset + "/max", "avg", maxMean);

		// Handle mipmaps here
		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] rawMipmaps = new RandomAccessibleInterval[numScales];


		final double[][] scales = new double[numScales][];

		/*
		 * raw pixels for mipmap level
		 * can be reused when transformation updates
		 */
		for (int s = 0; s < numScales; ++s) {
			int scale = 1 << s;
			String scaleDataset = inputDataset + "/s" + s;

			if( s > 0 ) {
				int[] dfs = n5.getAttribute(scaleDataset, "downsamplingFactors", int[].class);
				scale = dfs[0];
			}
			double inverseScale = 1.0 / scale;

			rawMipmaps[s] =
					N5Utils.openVolatile(
							n5,
							scaleDataset);

			scales[s] = new double[]{scale, scale, scale};
		}
		System.out.println("Done reading rawMipmaps");
		System.out.println("Time: " + LocalDateTime.now());

		RandomAccessibleInterval<UnsignedByteType> dsCost = N5Utils.openVolatile( n5, costDataset );
		costDSFactors = n5.getAttribute(costDataset, "downsamplingFactors", double[].class);

		RandomAccessibleInterval<DoubleType> cost =
			Views.interval(
				Views.raster(
					RealViews.affineReal(
						Views.interpolate(
								Views.extendBorder(
									Converters.convert(
										(RandomAccessibleInterval<UnsignedByteType>)Views.permute(dsCost, 1, 2),
										(a, x) -> x.setReal(a.getRealDouble()),
										new DoubleType())),
								new NLinearInterpolatorFactory<>()),
						new Translation3D(
								(costDSFactors[0] - 1) * 0.5,
								(costDSFactors[2] - 1) * 0.5,
								(costDSFactors[1] - 1) * 0.5))),
				rawMipmaps[0]);

		System.out.println("rawMipmaps[0]: " + rawMipmaps[0].dimension(0) + " " + rawMipmaps[0].dimension(1) + " " + rawMipmaps[0].dimension(2));
		System.out.println("downsampled cost: " + dsCost.dimension(0) + " " + dsCost.dimension(1) + " " + dsCost.dimension(2));

		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc - 2)));

		FinalInterval sourceInterval = null;

        sourceInterval = new FinalInterval(
            new long[] {0, 0, 0},
            new long[] {dimensions[0] - 1, dimensions[2] - 1, dimensions[1] -1});// FIXME double check this dimension swap, it came from saalfeld's hot-knife ViewFlattened

		/* range/heightmap visualization */
		final IntervalView<DoubleType> zRange = Views.interval(
				zRange(minMean, maxMean, 255, 1),
				new long[]{0, 0, Math.round(minMean) - padding},
				new long[]{sourceInterval.dimension(0), sourceInterval.dimension(2), Math.round(maxMean) + padding});

		final Interval zCropInterval = zRange;

		// TODO: Height maps contain NaN's and sometimes strong negative numbers
		//ImageJFunctions.show(min);

		final FlattenTransform ft = new FlattenTransform<>(
					Transform.scaleAndShiftHeightFieldAndValues(min, downsamplingFactors),
					Transform.scaleAndShiftHeightFieldAndValues(max, downsamplingFactors),
					(minMean + 0.5) * downsamplingFactors[2] - 0.5,
					(maxMean + 0.5) * downsamplingFactors[2] - 0.5);

		final RandomAccessibleInterval<DoubleType> heightmapRai =
				Transform.createTransformedInterval(
						zRange,
						zCropInterval,
						ft,
						new DoubleType(0));

		//RandomAccessibleIntervalSource heightmapOverlay = new RandomAccessibleIntervalSource(Views.permute(heightmapRai, 1, 2), new DoubleType(), "heightmap");
		RandomAccessibleIntervalSource heightmapOverlay = new RandomAccessibleIntervalSource(heightmapRai, new DoubleType(), "heightmap");

//		bdv = BdvFunctions.show(Views.permute(heightmapOverlay, 1, 2), "", BdvOptions.options().addTo(bdv));
//		//bdv = BdvFunctions.show(transformedSource, "", BdvOptions.options().addTo(bdv));
//		bdv.setDisplayRangeBounds(0, 255);
//		bdv.setDisplayRange(0, 255);
//		bdv.setColor(new ARGBType(0xff00ff00));

		// Make the Sources that we will use in BigWarp
		final Source<?>[] fAndO = makeFlatAndOriginalSource(rawMipmaps, scales, voxelDimensions, inputDataset, sourceInterval, useVolatile, null, queue);

//		BigWarp.BigWarpData bwData = BigWarpInit.createBigWarpData(new Source[]{fAndO[0]},
//                                                                   new Source[]{heightmapOverlay},
//                                                                   new String[]{"Flat", "heightmapOverlay"});

		new ImageJ();// FIXME debugging

		//Source<?> costSource = makeCostSource(costMipmaps, scales, voxelDimensions, inputDataset, sourceInterval, useVolatile, null, queue);

		BigWarp.BigWarpData bwData = BigWarpInit.createBigWarpData(new Source[]{fAndO[0]},
                                                                   new Source[]{fAndO[1], heightmapOverlay},
                                                                   new String[]{"Flat", "Original", "heightmapOverlay"});

		System.out.println("Source dimensions: " +
				fAndO[1].getSource(0,0).dimension(0) + " " +
				fAndO[1].getSource(0,0).dimension(1) + " " +
				fAndO[1].getSource(0,0).dimension(2));

		ProgressWriterIJ progress = new ProgressWriterIJ();

		@SuppressWarnings( "unchecked" )
		BigWarp bw = new BigWarp( bwData, n5.getBasePath(), progress );

		// Adjust the contrast for the overlay
		bw.getSetupAssignments().getConverterSetups().get( 2 ).setDisplayRange( 0, 255 );
		bw.getSetupAssignments().getConverterSetups().get( 2 ).setColor(new ARGBType(0x00ff0000));
//		bw.getSetupAssignments().getConverterSetups().get( 3 ).setDisplayRange( 0, 1000 );
//		bw.getSetupAssignments().getConverterSetups().get( 3 ).setColor(new ARGBType(0xff00ff00));


		// Load in a bunch of global-ish variables to BigWarp
		bw.setImagej(imagej);
		bw.setIsMovingDisplayTransformed(true);
        //bw.setIsMovingDisplayTransformed(false);
		bw.setFullSizeInterval(sourceInterval);
		bw.setRawMipmaps(rawMipmaps);
		bw.setQueue(queue);
		bw.setMinHeightmap(min);
		bw.setMaxHeightmap(max);
		bw.setVoxelDimensions(voxelDimensions);
		bw.setScales(scales);
		bw.setName(inputDataset);
		bw.setUseVolatile(useVolatile);
		bw.setN5Path(n5Path);
		bw.setFlattenSubContainer(flattenDataset);
		bw.setMinMean(minMean);
		bw.setMaxMean(maxMean);

		bw.setCost(cost);
		bw.setCostStepData((int) costDSFactors[0]);
		bw.setHeightmapDownsamplingFactors(downsamplingFactors);

		bw.setUpdateWarpOnChange(false);

		// Trigger the first computation of the flatten transform
		bw.restimateTransformation(true);

		System.out.println("Time: " + LocalDateTime.now());

		System.out.println("\nAdditional usage instructions:");
		System.out.println("f - Apply flatten transform using existing nails");
		System.out.println("ctrl-n - Generate grid of nails centered around current nail placed along existing heightmap");
		System.out.println("ctrl-f - Export flatten heightmap and nails\n");

		return null;
	}


}
