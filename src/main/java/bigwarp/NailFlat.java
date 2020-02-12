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

import bdv.util.*;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.*;
import org.janelia.saalfeldlab.hotknife.FlattenTransform;
import org.janelia.saalfeldlab.hotknife.util.Show;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.RawCompression;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import bdv.ij.util.ProgressWriterIJ;
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

	static int costMipmapToUse = 1;

	@Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i /nrs/flyem/tmp/VNC.n5")
	private String n5Path = "/nrs/flyem/alignment/kyle/nail_test.n5";

	@Option(names = {"-d", "--dataset"}, required = true, description = "Input dataset -d '/zcorr/Sec22___20200106_083252'")
	private String inputDataset = "/volumes/input";

	@Option(names = {"-s", "--cost"}, required = true, description = "Cost dataset -d '/cost/Sec22___20200110_133809'")
	private String costDataset = "/volumes/cost";

	@Option(names = {"-f", "--flatten"}, required = true, description = "Flatten subcontainer -f '/flatten/Sec22___20200110_133809'")
	private String flattenDataset = "/flatten";

	@Option(names = {"-u", "--resume"}, required = false, description = "Resume a flattening session by loading min/max from the flatten dataset")
	private boolean resume = false;

	@Option(names = {"--heightmaps"}, required = true, description = "Dataset for the min heightmap -f '/flatten/Sec22___20200110_133809/heightmaps' or full path to HDF5")
	private String heightmapDataset = null;

//	@Option(names = {"--min"}, required = false, description = "Dataset for the min heightmap -f '/flatten/Sec22___20200110_133809/heightmaps/min' or full path to HDF5")
//	private String minDataset = null;
//
//	@Option(names = {"--max"}, required = false, description = "Dataset for the max heightmap -f '/flatten/Sec22___20200110_133809/heightmaps/max' or full path to HDF5")
//	private String maxDataset = null;

	private boolean useVolatile = true;
	private long padding = 2000;

	FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", 1, 1, 1);

	public static final void main(String... args) {
		if( args.length == 0 )
			args = new String[]{
			        "-i", "/nrs/flyem/tmp/VNC.n5",
					"-d", "/zcorr/Sec03___20200110_121405",
					"-f", "/heightfields/Sec03_20200206_162201_kyle01",
					"-s", "/cost/Sec03_20200206_162201",
//					"-u"
					"--heightmaps", "/heightfields/Sec03_20200206_162201/s1"
//					"--min", "/heightfields/Sec02_20200206_152322/s7/min",
//					"--max", "/heightfields/Sec02_20200206_152322/s7/max"
					};

//			args = new String[]{
//			        "-i", "/nrs/flyem/tmp/VNC.n5",
//					"-d", "/zcorr/Sec02___20200114_103029",
//					"-f", "/heightfields/Sec02_20200206_152322_s1_kyle01",
//					"-s", "/cost/Sec02_20200206_152322",
////					"-u"
//					"--heightmaps", "/heightfields/Sec02_20200206_152322/s1"
////					"--min", "/heightfields/Sec02_20200206_152322/s7/min",
////					"--max", "/heightfields/Sec02_20200206_152322/s7/max"
//					};



		CommandLine.call(new NailFlat(), args);
		//new NailFlat().call();
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

		String minDataset, maxDataset;

		if( resume ) {
			minDataset = flattenDataset + BigWarp.minFaceDatasetName;
			maxDataset = flattenDataset + BigWarp.maxFaceDatasetName;
			// TODO add support for loading nails
		} else {
			minDataset = heightmapDataset + "/min";
			maxDataset = heightmapDataset + "/max";
		}

		ExecutorService exec = Executors.newFixedThreadPool(8);

		System.out.println("Start time: " + LocalDateTime.now());

		double minMean, maxMean;

		double[] heightmapScales = n5.getAttribute(heightmapDataset, "downsamplingFactors", double[].class);
		if (heightmapScales == null)
			heightmapScales = new double[] {1, 1, 1};

		// Min heightmap: Load from N5 if possible
		if( minDataset != null && n5.exists(minDataset) ) {
			System.out.println("Loading min face from N5 " + minDataset);
			min = N5Utils.open(n5, minDataset);
			//minMean = n5.getAttribute(minDataset, "mean", double.class);
			minMean = n5.getAttribute(minDataset, "avg", double.class);
		} else {
		    System.out.println("Min heightmap is missing from: " + minDataset );
		    throw new Exception("Missing heightmap");
        }
		System.out.println("Time: " + LocalDateTime.now());

		// Min heightmap: Load from N5 if possible
		if( maxDataset != null && n5.exists(maxDataset) ) {
			System.out.println("Loading max face from N5 " + maxDataset);
			max = N5Utils.open(n5, maxDataset);
			//maxMean = n5.getAttribute(maxDataset, "mean", double.class);
			maxMean = n5.getAttribute(maxDataset, "avg", double.class);
		}  else {
		    System.out.println("Max heightmap is missing from: " + maxDataset );
		    throw new Exception("Missing heightmap");
        }
		System.out.println("Time: " + LocalDateTime.now());

		System.out.println("Min heightmap: " + min.dimension(0) + " " + min.dimension(1) + " " + min.dimension(2));
		System.out.println("Max heightmap: " + max.dimension(0) + " " + max.dimension(1) + " " + max.dimension(2));

		// Setup the flatten dataset
		N5FSWriter n5w = new N5FSWriter(n5Path);
		N5Utils.save(min, n5w, flattenDataset + BigWarp.minFaceDatasetName, new int[]{1024, 1024}, new RawCompression());
		n5w.setAttribute(flattenDataset + BigWarp.minFaceDatasetName, "avg", minMean);

		N5Utils.save(max, n5w, flattenDataset + BigWarp.maxFaceDatasetName, new int[]{1024, 1024}, new RawCompression());
		n5w.setAttribute(flattenDataset + BigWarp.maxFaceDatasetName, "avg", maxMean);

		final RandomAccessibleInterval<UnsignedByteType> costMipmap =
				N5Utils.openVolatile(
						n5,
						costDataset + "/s" + costMipmapToUse);

		double[] costDownsample = n5.getAttribute(costDataset + "/s" + costMipmapToUse, "downsamplingFactors", double[].class);
		long costStep = (long) costDownsample[0];

		// Adjust heightmaps for half pixel offset
//		min =
//			Views.interval(
//				Views.raster(
//					RealViews.affineReal(
//							Views.interpolate(
//									Views.extendBorder(min),
//									new NLinearInterpolatorFactory<>()),
//							new Translation2D(( costStep - 1 ) * 0.5, ( costStep - 1 ) * 0.5))),
//				min);
//
//		max =
//			Views.interval(
//				Views.raster(
//					RealViews.affineReal(
//							Views.interpolate(
//									Views.extendBorder(max),
//									new NLinearInterpolatorFactory<>()),
//							new Translation2D(( costStep - 1 ) * 0.5, ( costStep - 1 ) * 0.5))),
//				max);

		// Handle mipmaps here
		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] rawMipmaps = new RandomAccessibleInterval[numScales];

		//RandomAccessibleInterval<UnsignedByteType> cost = N5Utils.openVolatile(n5, costDataset + "/s0");

//		@SuppressWarnings("unchecked")
//		final RandomAccessibleInterval<UnsignedByteType>[] costMipmaps = new RandomAccessibleInterval[numScales];

		final double[][] scales = new double[numScales][];

		/*
		 * raw pixels for mipmap level
		 * can be reused when transformation updates
		 */
		for (int s = 0; s < numScales; ++s) {
			int scale = 1 << s;
			String scaleDataset = inputDataset + "/s" + s;

			if( s > 0 ) {
				int[] downsamplingFactors = n5.getAttribute(scaleDataset, "downsamplingFactors", int[].class);
				scale = downsamplingFactors[0];
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



		System.out.println("rawMipmaps[0]: " + rawMipmaps[0].dimension(0) + " " + rawMipmaps[0].dimension(1) + " " + rawMipmaps[0].dimension(2));
		System.out.println("costMipmap: " + costMipmap.dimension(0) + " " + costMipmap.dimension(1) + " " + costMipmap.dimension(2));

		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc - 2)));

		FinalInterval sourceInterval = null;
//		if( max != null && min != null ) {
//			// If we have heightmaps, then we can define a crop interval for source rendering
//			DoubleType maxMean = SemaUtils.getAvgValue(max);
//			DoubleType minMean = SemaUtils.getAvgValue(min);
//
//			sourceInterval = new FinalInterval(
//				new long[] {0, 0, Math.round(minMean.get()) - padding},
//				new long[] {dimensions[0] - 1, dimensions[2] - 1, Math.round(maxMean.get()) + padding});
//		} else {
        sourceInterval = new FinalInterval(
            new long[] {0, 0, 0},
            new long[] {dimensions[0] - 1, dimensions[2] - 1, dimensions[1] -1});// FIXME double check this dimension swap, it came from saalfeld's hot-knife ViewFlattened

		double heightmapScale = heightmapScales[2];//					(minAvg + 0.5) * heightmapScale - 0.5,

		/* range/heightmap visualization */
		final IntervalView<DoubleType> zRange = Views.interval(
				zRange(( minMean + 0.5 ) * heightmapScale - 0.5,
						( maxMean + 0.5 ) * heightmapScale - 0.5,
						255,
						1),
				new long[]{0, 0, Math.round(( minMean + 0.5 ) * heightmapScale - 0.5) - padding},
				new long[]{rawMipmaps[0].dimension(0), rawMipmaps[0].dimension(2), Math.round(( maxMean + 0.5 ) * heightmapScale - 0.5) + padding});

		final Interval zCropInterval = zRange;

		System.out.println("range/heightmap: " + zRange);
		System.out.println("min: " + (( minMean + 0.5 ) * heightmapScale - 0.5) + " max: " + (( maxMean + 0.5 ) * heightmapScale - 0.5));

		// TODO: Height maps contain NaN's and sometimes strong negative numbers
		//ImageJFunctions.show(min);

		double transformScaleX = costStep;
		double transformScaleY = costStep;
		final Scale2D transformScale = new Scale2D(transformScaleX, transformScaleY);

		// TODO: note that the avg values need to account for the downsampling factor

		final FlattenTransform ft = new FlattenTransform(
								RealViews.affine(
										Views.interpolate(
												Views.extendBorder(min),
												new NLinearInterpolatorFactory<>()),
										transformScale),
								RealViews.affine(
										Views.interpolate(
												Views.extendBorder(max),
												new NLinearInterpolatorFactory<>()),
										transformScale),
				( minMean + 0.5 ) * heightmapScale - 0.5,
				( maxMean + 0.5 ) * heightmapScale - 0.5);

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
		bw.setFullSizeInterval(Intervals.createMinMax(0, 0, 0, dimensions[0]-1, dimensions[2]-1, dimensions[1]-1));
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

		//bw.setCost(cost);

		RandomAccessibleInterval<DoubleType> costDouble =
				Converters.convert(
						costMipmap,
						(a, b) -> b.setReal(a.getRealDouble()),
						new DoubleType());
		RandomAccessibleInterval<DoubleType> costInterp =
				Transform.createTransformedInterval(
						costDouble,
						rawMipmaps[0],
						new Translation3D(( costDownsample[0] - 1 ) * 0.5, 0, ( costDownsample[2] - 1 ) * 0.5),
//						new ScaleAndTranslation(
//								scales[costMipmapToUse],
//								new double[]{4, 4, 4}),
						new DoubleType(0));

//		BdvFunctions.show(costDouble, "costDouble");
//		BdvFunctions.show(costInterp, "interp");

		bw.setCost(costInterp);

		bw.setCostStepData((int) costStep);
		bw.setHeightmapScale(heightmapScale);
		bw.setUpdateWarpOnChange(false);

		// Load nails from N5
		//System.out.println(bw.getTransformation());
		//bw.loadNails(n5Path, flattenDataset + nailDataset);// FIXME and see the --resume argument

		// Trigger the first computation of the flatten transform
		bw.restimateTransformation(true);

		System.out.println("Time: " + LocalDateTime.now());

		System.out.println("\nAdditional usage instructions:");
		System.out.println("f - Apply flatten transform using existing nails");
		System.out.println("ctrl-n - Generate grid of nails centered around current nail placed along existing heightmap");
		System.out.println("ctrl-f - Export flatten heightmap and nails\n");

		return null;
	}

	private Source<?> makeCostSource(RandomAccessibleInterval<UnsignedByteType>[] costMipmaps, double[][] scales, FinalVoxelDimensions voxelDimensions, String inputDataset, FinalInterval cropInterval, boolean useVolatile, FlattenTransform ft, SharedQueue queue) throws IOException {
		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] mipmaps = new RandomAccessibleInterval[scales.length];

		for (int s = 0; s < scales.length; ++s) {

			final int scale = (int) scales[s][0];
			final double inverseScale = 1.0 / scale;

			final RealTransformSequence transformSequenceFlat = new RealTransformSequence();
			final Scale3D scale3D = new Scale3D(inverseScale, inverseScale, inverseScale);
			final Translation3D shift = new Translation3D(0.5 * (scale - 1), 0.5 * (scale - 1), 0.5 * (scale - 1));
			transformSequenceFlat.add(shift);
			if( ft != null )
				transformSequenceFlat.add(ft.inverse());
			transformSequenceFlat.add(shift.inverse());
			transformSequenceFlat.add(scale3D);

			final RandomAccessibleInterval<UnsignedByteType> originalSource =
					Transform.createTransformedInterval(
							//Views.permute(costMipmaps[s], 1, 2),
							costMipmaps[s],
							cropInterval,
							scale3D,
							new UnsignedByteType(0));

			final SubsampleIntervalView<UnsignedByteType> subsampledOriginalSource = Views.subsample(originalSource, scale);
			final RandomAccessibleInterval<UnsignedByteType> cachedOriginalSource = Show.wrapAsVolatileCachedCellImg(subsampledOriginalSource, new int[]{32, 32, 32});

			if( useVolatile ) {
				mipmaps[s] = cachedOriginalSource;
			} else {
				mipmaps[s] = subsampledOriginalSource;
			}
			scales[s] = new double[]{scale, scale, scale};
		}

		/*
		 * update when transforms change
		 */

		final RandomAccessibleIntervalMipmapSource<?> mipmapSourceOriginal =
				new RandomAccessibleIntervalMipmapSource<>(
						mipmaps,
						new UnsignedByteType(),
						scales,
						voxelDimensions,
						inputDataset);

		final Source<?> volatileMipmapSourceFlat;
		final Source<?> volatileMipmapSourceOriginal;
		if ( useVolatile ) {
			volatileMipmapSourceOriginal = mipmapSourceOriginal.asVolatile(queue);
		} else {
			volatileMipmapSourceOriginal = mipmapSourceOriginal;
		}

		return volatileMipmapSourceOriginal;
	}


}
