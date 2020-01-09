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
import net.imglib2.cache.img.CachedCellImg;
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
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.File;
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

//	@Option(names = {"-t", "--cost"}, required = false, description = "Cost dataset -t '/slab-26-cost'")
//	private String costDataset = "/volumes/cost";

	@Option(names = {"-f", "--flatten"}, required = true, description = "Flatten subcontainer -f '/slab-26-flatten'")
	private String flattenDataset = "/flatten";

	@Option(names = {"-s", "--sectionName"}, required = true, description = "Section name, must correspond to /nrs/flyem/alignment/Z1217-19m/VNC/<SectionName> -s 'Sec24'")
	private String sectionName = "Sec24";

	private double transformScaleX = 1;
	private double transformScaleY = 1;

	private boolean useVolatile = true;
	private long padding = 20;

	FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", 1, 1, 1);

	public static final void main(String... args) throws IOException, InterruptedException, ExecutionException, SpimDataException {
		if( args.length == 0 )
			args = new String[]{"-i", "/nrs/flyem/tmp/VNC.n5", "-d", "/zcorr/Sec24___20200106_082231", "-f", "/flatten/Sec24___20200106_082231", "-s", "Sec24"};

		CommandLine.call(new NailFlat(), args);
		//new NailFlat().call();
	}

	public static String sectionDirectory(String sectionName) {
		return "/nrs/flyem/alignment/Z1217-19m/VNC/" + sectionName;
	}

	@Override
	public final Void call() throws IOException, InterruptedException, ExecutionException, SpimDataException {
		net.imagej.ImageJ imagej = new net.imagej.ImageJ();
		final N5FSReader n5 = new N5FSReader(n5Path);

		// Extract metadata from input
		final int numScales = n5.list(inputDataset).length;
		final long[] dimensions = n5.getDatasetAttributes(inputDataset + "/s0").getDimensions();

//		// Load cost
//        RandomAccessibleInterval<UnsignedByteType> cost = null;
//        final RandomAccessibleInterval<DoubleType> costDouble = null;
//        if( n5.exists(costDataset) ) {
//            if( n5.exists(costDataset + "/s0") )
//			    cost = N5Utils.open(n5, costDataset + "/s0");
//            else
//                cost = N5Utils.open(n5, costDataset);
//		} else {
//        	System.out.println("Missing cost dataset in n5.");
//			//throw new IOException("Missing cost dataset");
//		}

//		FinalInterval ivl = Intervals.createMinMax(0, 0, 0, cost.dimension(0), cost.dimension(1), cost.dimension(2));
//		Img<DoubleType> costDouble = imagej.op().create().img(ivl, new DoubleType());
//        copyRealInto(cost, costDouble);
        //final RandomAccessibleInterval<DoubleType> costDouble = Converters.convert(cost, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
        //ImageJFunctions.wrap(costDouble, "CostDouble").show();

        // Load/compute min heightmap and compute average value
		RandomAccessibleInterval<DoubleType> min = null;
		// Load from N5 if possible, if not, look for an HDF5
		if( n5.exists(flattenDataset + BigWarp.minFaceDatasetName) ) {
			System.out.println("Loading min face");
			min = N5Utils.open(n5, flattenDataset + BigWarp.minFaceDatasetName);
		} else if( new File(sectionDirectory(sectionName) + "/" + sectionName + "-bottom.h5").exists() ) {
			final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(sectionDirectory(sectionName) + "/" + sectionName + "-bottom.h5");
			final N5HDF5Reader hdf5 = new N5HDF5Reader(hdf5Reader, new int[]{128, 128, 128});
			final RandomAccessibleInterval<FloatType> floats = N5Utils.openVolatile(hdf5, "/volume");
			RandomAccessibleInterval<DoubleType> minConv = Converters.convert(floats, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
			N5FSWriter n5w = new N5FSWriter(n5Path);
			N5Utils.save(minConv, n5w, flattenDataset + BigWarp.minFaceDatasetName, new int[]{512, 512}, new GzipCompression());

			min = N5Utils.open(n5, flattenDataset + BigWarp.minFaceDatasetName);
		}
//		} else if( cost != null ) {
//			// This code is for using a precomputed cost function
//			System.out.println("Computing min face");
//			RandomAccessibleInterval<IntType> intMin = getScaledSurfaceMap(getBotImg(costDouble, imagej.op()), 0, dimensions[0], dimensions[2], imagej.op());
//			min = Converters.convert(intMin, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
//		} else {
//			throw new IOException("Missing min face and cost");
//		}
		//ImageJFunctions.wrap(min, "Min").show();
		//DoubleType minMean = SemaUtils.getAvgValue(min);

		// Load/compute max heightmap and compute average value
		RandomAccessibleInterval<DoubleType> max = null;
		if( n5.exists(flattenDataset + BigWarp.maxFaceDatasetName) ) {
			System.out.println("Loading max face");
			max = N5Utils.open(n5, flattenDataset + BigWarp.maxFaceDatasetName);
		} else if( new File(sectionDirectory(sectionName) + "/" + sectionName + "-top.h5").exists() ) {
			final IHDF5Reader hdf5Reader = HDF5Factory.openForReading(sectionDirectory(sectionName) + "/" + sectionName + "-top.h5");
			final N5HDF5Reader hdf5 = new N5HDF5Reader(hdf5Reader, new int[]{128, 128, 128});
			final RandomAccessibleInterval<FloatType> floats = N5Utils.openVolatile(hdf5, "/volume");
			RandomAccessibleInterval<DoubleType> maxConv = Converters.convert(floats, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
			N5FSWriter n5w = new N5FSWriter(n5Path);
			N5Utils.save(maxConv, n5w, flattenDataset + BigWarp.maxFaceDatasetName, new int[]{512, 512}, new GzipCompression());

			max = N5Utils.open(n5, flattenDataset + BigWarp.maxFaceDatasetName);
		}
//		} else if( cost != null ) {
//			System.out.println("Computing max face");
//			RandomAccessibleInterval<IntType> intMax = getScaledSurfaceMap(getTopImg(costDouble, imagej.op()), cost.dimension(2) / 2, dimensions[0], dimensions[2], imagej.op());
//			max = Converters.convert(intMax, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
//		} else {
//			throw new IOException("Missing max face and cost");
//		}
		//ImageJFunctions.wrap(max, "Max").show();
		//DoubleType maxMean = SemaUtils.getAvgValue(max);

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

		FinalInterval sourceInterval = null;
		if( max != null && min != null ) {
			// If we have heightmaps, then we can define a crop interval for source rendering
			DoubleType maxMean = SemaUtils.getAvgValue(max);
			DoubleType minMean = SemaUtils.getAvgValue(min);

			sourceInterval = new FinalInterval(
				new long[] {0, 0, Math.round(minMean.get()) - padding},
				new long[] {dimensions[0] - 1, dimensions[2] - 1, Math.round(maxMean.get()) + padding});
		} else {
			sourceInterval = new FinalInterval(
				new long[] {0, 0, 0},
				new long[] {dimensions[0] - 1, dimensions[2] - 1, dimensions[1] -1});// FIXME double check this dimension swap, it came from saalfeld's hot-knife ViewFlattened
		}


		final Source<?>[] fAndO = makeFlatAndOriginalSource(rawMipmaps, scales, voxelDimensions, inputDataset, sourceInterval, useVolatile, null, queue);


		BigWarp.BigWarpData bwData = BigWarpInit.createBigWarpData(new Source[]{fAndO[0]},
                                                                   new Source[]{fAndO[1]},
                                                                   new String[]{"Flat", "Original"});

		ProgressWriterIJ progress = new ProgressWriterIJ();

		@SuppressWarnings( "unchecked" )
		BigWarp bw = new BigWarp( bwData, n5.getBasePath(), progress );

		bw.setImagej(imagej);
		bw.setIsMovingDisplayTransformed(true);
		bw.setFullSizeInterval(Intervals.createMinMax(0, 0, 0, dimensions[0], dimensions[1], dimensions[2]));
		//bw.setSourceCostImg(costDouble);
		bw.restimateTransformation();
		bw.setRawMipmaps(rawMipmaps);
		bw.setUseVolatile(useVolatile);
		bw.setQueue(queue);
		bw.setMinHeightmap(min);
		bw.setMaxHeightmap(max);
		//bw.setCost(cost);
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
