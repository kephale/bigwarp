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

import bdv.ij.util.ProgressWriterIJ;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.SubsampleIntervalView;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.hotknife.FlattenTransform;
import org.janelia.saalfeldlab.hotknife.util.Show;
import org.janelia.saalfeldlab.hotknife.util.Transform;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import static bigwarp.BigWarp.makeFlatAndOriginalSource;
import static bigwarp.SemaUtils.copyRealInto;
import static bigwarp.SemaUtils.flipCost;
import static net.preibisch.surface.SurfaceFitCommand.*;

/**
 *
 *
 * @author Kyle Harrington &lt;janelia@kyleharrington.com&gt;
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class NailFlat implements Callable<Void> {

	@Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i $HOME/fib19.n5")
	private String n5Path = "/nrs/flyem/alignment/kyle/nail_test.n5";

	@Option(names = {"-d", "--dataset"}, required = true, description = "Input dataset -d '/slab-26'")
	private String inputDataset = "/volumes/input";

	@Option(names = {"-t", "--cost"}, required = true, description = "Cost dataset -t '/slab-26-cost'")
	private String costDataset = "/volumes/cost";

	@Option(names = {"-f", "--flatten"}, required = true, description = "Flatten subcontainer -f '/slab-26-flatten'")
	private String flattenDataset = "/flatten";

	private double transformScaleX = 1;
	private double transformScaleY = 1;

	private boolean useVolatile = true;
	private long padding = 20;

	FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", 1, 1, 1);

	public static void main(final String... args) throws IOException, InterruptedException, ExecutionException, SpimDataException {
		//CommandLine.call(new NailFlat(), args);
		new NailFlat().call();
	}


	@Override
	public final Void call() throws IOException, InterruptedException, ExecutionException, SpimDataException {
		net.imagej.ImageJ imagej = new net.imagej.ImageJ();
		final N5FSReader n5 = new N5FSReader(n5Path);

		// Extract metadata from input
		final int numScales = n5.list(inputDataset).length;
		final long[] dimensions = n5.getDatasetAttributes(inputDataset + "/s0").getDimensions();

		// Load cost
        RandomAccessibleInterval<UnsignedByteType> cost = null;
        if( n5.exists(costDataset) ) {
            if( n5.exists(costDataset + "/s0") )
			    cost = N5Utils.open(n5, costDataset + "/s0");
            else
                cost = N5Utils.open(n5, costDataset);
		} else {
        	//System.out.println("Missing cost dataset");
			throw new IOException("Missing cost dataset");
		}

		FinalInterval ivl = Intervals.createMinMax(0, 0, 0, cost.dimension(0), cost.dimension(1), cost.dimension(2));
		Img<DoubleType> costDouble = imagej.op().create().img(ivl, new DoubleType());
        copyRealInto(cost, costDouble);
        //final RandomAccessibleInterval<DoubleType> costDouble = Converters.convert(cost, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
        //ImageJFunctions.wrap(costDouble, "CostDouble").show();

        // Load/compute min heightmap and compute average value
		final RandomAccessibleInterval<DoubleType> min;
		if( n5.exists(flattenDataset + BigWarp.minFaceDatasetName) ) {
			System.out.println("Loading min face");
			min = N5Utils.open(n5, flattenDataset + BigWarp.minFaceDatasetName);
		} else if( cost != null ) {
			System.out.println("Computing min face");
			RandomAccessibleInterval<IntType> intMin = getScaledSurfaceMap(getBotImg(costDouble, imagej.op()), 0, dimensions[0], dimensions[2], imagej.op());
			min = Converters.convert(intMin, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
		} else {
			throw new IOException("Missing min face and cost");
		}
		//ImageJFunctions.wrap(min, "Min").show();
		DoubleType minMean = SemaUtils.getAvgValue(min);

		// Load/compute max heightmap and compute average value
		final RandomAccessibleInterval<DoubleType> max;
		if( n5.exists(flattenDataset + BigWarp.maxFaceDatasetName) ) {
			System.out.println("Loading max face");
			max = N5Utils.open(n5, flattenDataset + BigWarp.maxFaceDatasetName);
		} else if( cost != null ) {
			System.out.println("Computing max face");
			RandomAccessibleInterval<IntType> intMax = getScaledSurfaceMap(getTopImg(costDouble, imagej.op()), cost.dimension(2) / 2, dimensions[0], dimensions[2], imagej.op());
			max = Converters.convert(intMax, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
		} else {
			throw new IOException("Missing max face and cost");
		}
		//ImageJFunctions.wrap(max, "Max").show();
		DoubleType maxMean = SemaUtils.getAvgValue(max);

		System.out.println("Mean min heightmap: " + minMean.get());
		System.out.println("Mean max heightmap: " + maxMean.get());

		final FinalInterval cropInterval = new FinalInterval(
				new long[] {0, 0, Math.round(minMean.get()) - padding},
				new long[] {dimensions[0] - 1, dimensions[2] - 1, Math.round(maxMean.get()) + padding});

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

		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc - 2)));
		final Source<?>[] fAndO = makeFlatAndOriginalSource(rawMipmaps, scales, voxelDimensions, inputDataset, cropInterval, useVolatile, null, queue);


		BigWarp.BigWarpData bwData = BigWarpInit.createBigWarpData(new Source[]{fAndO[0]},
                                                                   new Source[]{fAndO[1]},
                                                                   new String[]{"Flat", "Original"});

		ProgressWriterIJ progress = new ProgressWriterIJ();

		@SuppressWarnings( "unchecked" )
		BigWarp bw = new BigWarp( bwData, n5.getBasePath(), progress );

		bw.setImagej(imagej);
		bw.setIsMovingDisplayTransformed(true);
		bw.setFullSizeInterval(Intervals.createMinMax(0, 0, 0, dimensions[0], dimensions[1], dimensions[2]));
		bw.setSourceCostImg(costDouble);
		bw.restimateTransformation();
		bw.setRawMipmaps(rawMipmaps);
		bw.setUseVolatile(useVolatile);
		bw.setQueue(queue);
		bw.setMinHeightmap(min);
		bw.setMaxHeightmap(max);
		bw.setCost(cost);
		bw.setVoxelDimensions(voxelDimensions);
		bw.setScales(scales);
		bw.setName(inputDataset);
		bw.setUseVolatile(useVolatile);
		bw.setN5Path(n5Path);
		bw.setFlattenSubContainer(flattenDataset);

		//System.out.println(bw.getTransformation());
		//bw.loadNails(n5Path, flattenDataset + nailDataset);// FIXME

		return null;
	}




}
