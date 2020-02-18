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
        static String[] argsSec02 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec02___20200114_103029",
            "-s", "/cost/Sec02_20200206_152322",
            "--heightmaps", "/heightfields/Sec02_20200206_152322/s1"
    };

    static String[] argsSec03 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec03___20200110_121405",
            "-s", "/cost/Sec03_20200215_1557",
			"-f", "/heightfields/Sec03_20200215_1557_s1_sp3_khcheck2",
            "--heightmaps", "/heightfields/Sec03_20200215_1557_s1_sp3"
    };

    static String[] argsSec04 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec04___20200110_114355",
            "-s", "/cost/Sec04_20200207_92236",
            "--heightmaps", "/heightfields/Sec04_20200207_92236/s1"
    };

    static String[] argsSec05 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec05___20200211_132910",
            "-s", "/cost/Sec05_20200213_1127",
            "--heightmaps", "/heightfields/Sec05_20200211_125137/s1"
    };

    static String[] argsSec06 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec06___20200130_110551",
            "-s", "/cost/Sec06",
            "--heightmaps", "/heightfields/Sec06/s1"
    };

    static String[] argsSec07 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec07___20200205_105604",
            "-f", "/heightfields/Sec07_20200207_113426_revision01",
            "-s", "/cost/Sec07_20200207_113426",
            "--heightmaps", "/heightfields/Sec07_20200207_113426/s1"
    };

    static String[] argsSec08 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec08___20200211_170942",
            "-f", "/heightfields/Sec08_20200215_1557_kh0",
            "-s", "/cost/Sec08_20200215_1557",
            "--heightmaps", "/heightfields/Sec08_20200215_1557/s1"
    };

//    static String[] argsSec09 = new String[]{
//            "-i", "/nrs/flyem/tmp/VNC.n5",
//            "-d", "/zcorr/Sec09___20200119_231012",
//            "-f", "/heightfields/Sec03_20200206_162201_revision01",
//            "-s", "/cost/Sec03_20200206_162201",
//            "--heightmaps", "/heightfields/Sec03_20200206_162201/s1"
//    };

    static String[] argsSec10 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec10___20200206_113538",
            "-s", "/cost/Sec10_20200208_155250",
            "--heightmaps", "/heightfields/Sec10_20200208_155250/s1"
    };

    static String[] argsSec11 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec11___20200106_081745",
            "-s", "/cost/Sec11_20200208_151432",
            "--heightmaps", "/heightfields/Sec11_20200208_151432/s1"
    };

    static String[] argsSec12 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec12___20200210_153413",
            "-s", "/cost/Sec12_20200210_175133",
            "--heightmaps", "/heightfields/Sec12_20200210_175133/s1"
    };

    static String[] argsSec13 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec13___20200110_160542",
            "-s", "/cost/Sec13_20200208_102626",
            "--heightmaps", "/heightfields/Sec13_20200208_102626/s1"
    };

	static String[] argsSec14scale1 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec14___20200106_085015",
            "-f", "/heightfields/Sec14_20200208_102754_s1_khtest",
            "-s", "/cost/Sec14_20200208_102754",
            "--costMipmapToUse", "1",
            "--heightmaps", "/heightfields/Sec14_20200208_102754/s1"
    };

    static String[] argsSec14scale3 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec14___20200106_085015",
            "-f", "/heightfields/Sec14_20200208_102754_s3_kh0",
            "-s", "/cost/Sec14_20200208_102754",
            "--costMipmapToUse", "3",
            "--heightmaps", "/heightfields/Sec14_20200208_102754/s3"
    };

    static String[] argsSec14 = argsSec14scale1;

    static String[] argsSec15 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec15___20200205_113313",
            "-s", "/cost/Sec15_20200208_102958",
            "--heightmaps", "/heightfields/Sec15_20200208_102958/s1"
    };

    static String[] argsSec16 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec16___20200207_111635",
            "-s", "/cost/Sec16_20200208_103706",
            "--heightmaps", "/heightfields/Sec16_20200208_103706/s1"
    };

    static String[] argsSec17 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec17___20200207_111717",
            "-s", "/cost/Sec17_20200208_151438",
            "--heightmaps", "/heightfields/Sec17_20200208_151438/s1"
    };

    static String[] argsSec18 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec18___20200206_133157",
            "-s", "/cost/Sec18_20200208_151851",
            "--heightmaps", "/heightfields/Sec18_20200208_151851/s1"
    };

    static String[] argsSec19 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec19___20200203_085722",
            "-s", "/cost/Sec19_20200208_95359",
            "--heightmaps", "/heightfields/Sec19_20200208_95359/s1"
    };

    static String[] argsSec20 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec20___20200207_113848",
            "-s", "/cost/Sec20_20200208_152048",
            "--heightmaps", "/heightfields/Sec20_20200208_152048/s1"
    };

    static String[] argsSec21 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec21___20200106_084220",
            "-s", "/cost/Sec21_20200207_112747",
            "--heightmaps", "/heightfields/Sec21_20200207_112747/s1"
    };

    static String[] argsSec22 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec22___20200106_083252",
            "-s", "/cost/Sec22_20200207_102130",
            "--heightmaps", "/heightfields/Sec22_20200207_102130/s1"
    };

    static String[] argsSec23 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec23___20200211_133418",
            "-s", "/cost/Sec23_20200211_113117",
            "--heightmaps", "/heightfields/Sec23_20200211_113117/s1"
    };

    static String[] argsSec24 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec24___20200106_082231",
            "-s", "/cost/Sec24_20200207_113242",
            "--heightmaps", "/heightfields/Sec24_20200207_113242/s1"
    };

    static String[] argsSec25 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec25___20200106_082123",
            "-s", "/cost/Sec25_20200207_102546",
            "--heightmaps", "/heightfields/Sec25_20200207_102546/s1"
    };

    static String[] argsSec26 = new String[]{
            "-i", "/nrs/flyem/tmp/VNC.n5",
            "-d", "/zcorr/Sec26___20200205_105758",
            "-s", "/cost/Sec26_20200207_102442",
            "--heightmaps", "/heightfields/Sec26_20200207_102442/s1"
    };

	@Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i /nrs/flyem/tmp/VNC.n5")
	private String n5Path = "/nrs/flyem/alignment/kyle/nail_test.n5";

	@Option(names = {"-d", "--dataset"}, required = true, description = "Input dataset -d '/zcorr/Sec22___20200106_083252'")
	private String inputDataset = "/volumes/input";

	@Option(names = {"-s", "--cost"}, required = true, description = "Cost dataset -d '/cost/Sec22___20200110_133809'")
	private String costDataset = "/volumes/cost";

	@Option(names = {"-f", "--flatten"}, required = false, description = "Flatten subcontainer -f '/flatten/Sec22___20200110_133809'")
	private String flattenDataset = null;

	@Option(names = {"-u", "--resume"}, required = false, description = "Resume a flattening session by loading min/max from the flatten dataset")
	private boolean resume = false;

	@Option(names = {"--heightmaps"}, required = true, description = "Dataset for the min heightmap -f '/flatten/Sec22___20200110_133809/heightmaps'")
	private String heightmapDataset = null;

	@Option(names = {"--costMipmapToUse"}, required = false, description = "Scale factor of the cost dataset to use. this MUST match the heightmap's downscale --costMipmapToUse 3")
	private int costMipmapToUse = 1;

//	@Option(names = {"--min"}, required = false, description = "Dataset for the min heightmap -f '/flatten/Sec22___20200110_133809/heightmaps/min' or full path to HDF5")
//	private String minDataset = null;
//
//	@Option(names = {"--max"}, required = false, description = "Dataset for the max heightmap -f '/flatten/Sec22___20200110_133809/heightmaps/max' or full path to HDF5")
//	private String maxDataset = null;

	private boolean useVolatile = true;
	private long padding = 2000;

	FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", 1, 1, 1);

	public static final void main(String... args) {

		if( args.length == 0 ) args = argsSec14;


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


		minDataset = heightmapDataset + "/min";
		maxDataset = heightmapDataset + "/max";

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
//		if( flattenDataset != null ) {
//			N5FSWriter n5w = new N5FSWriter(n5Path);
//			N5Utils.save(min, n5w, flattenDataset + BigWarp.minFaceDatasetName, new int[]{1024, 1024}, new RawCompression());
//			n5w.setAttribute(flattenDataset + BigWarp.minFaceDatasetName, "avg", minMean);
//
//			N5Utils.save(max, n5w, flattenDataset + BigWarp.maxFaceDatasetName, new int[]{1024, 1024}, new RawCompression());
//			n5w.setAttribute(flattenDataset + BigWarp.maxFaceDatasetName, "avg", maxMean);
//
//			n5w.setAttribute(flattenDataset, "sourceHeightmap", heightmapDataset);
//		}

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
				zRange(( minMean + 0.5 ) - 0.5,
						( maxMean + 0.5 ) - 0.5,
						255,
						1),
				new long[]{0, 0, Math.round(( minMean + 0.5 ) - 0.5) - padding},
				new long[]{rawMipmaps[0].dimension(0), rawMipmaps[0].dimension(2), Math.round(( maxMean + 0.5 ) - 0.5) + padding});

		final Interval zCropInterval = zRange;

		System.out.println("range/heightmap: " + zRange);
		System.out.println("min: " + (( minMean + 0.5 ) - 0.5) + " max: " + (( maxMean + 0.5 ) - 0.5));

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

//		options =
//
//		public BigWarpViewerOptions( final boolean is2d )
//		{
//			this.is2d = is2d;
//			this.screenScales(new double[] {0.5});
//			this.numRenderingThreads(12);
//		}

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
		bw.setSourceHeightmapDataset(heightmapDataset);

		//bw.setCost(cost);

		RandomAccessibleInterval<DoubleType> costDouble =
				Converters.convert(
						costMipmap,
						(a, b) -> b.setReal(a.getRealDouble()),
						new DoubleType());
		RandomAccessibleInterval<DoubleType> costInterp =
				Transform.createTransformedInterval(
						costDouble,
						costDouble,
						//rawMipmaps[0],
						new Translation3D(( costDownsample[0] - 1 ) * 0.5, 0, ( costDownsample[2] - 1 ) * 0.5),
//						new ScaleAndTranslation(
//								scales[costMipmapToUse],
//								new double[]{4, 4, 4}),
						new DoubleType(0));

//		BdvFunctions.show(costDouble, "costDouble");
//		BdvFunctions.show(costInterp, "interp");

		bw.setCost(costInterp);
		bw.setHeightmapScales(heightmapScales);

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
		System.out.println("ctrl-n - Generate grid of magic nails trained on existing nails");
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
