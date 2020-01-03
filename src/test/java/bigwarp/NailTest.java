package bigwarp;

import bdv.ij.util.ProgressWriterIJ;
import bdv.util.RandomAccessibleSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imagej.ops.OpService;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.converter.Converters;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;

import java.io.File;
import java.io.IOException;

import static net.preibisch.surface.SurfaceFitCommand.*;

public class NailTest {

    public static void main(final String[] args ) throws IOException, SpimDataException {
        net.imagej.ImageJ imagej = new net.imagej.ImageJ();

        String n5Path = "/groups/cardona/home/harringtonk/nrs_flyem/alignment/kyle/nail_test/flat_gaussian_cost.n5";
        //String n5Path = "/nrs/flyem/alignment/kyle/nail_test/flat_gaussian_cost.n5";

        String volumeDataset = "/volume";
        String costDataset = "/volume";

        boolean useVolatile = true;

		FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", 1, 1, 1);

		final N5FSReader n5 = new N5FSReader(n5Path);

		String fnLandmarks = "";

		// TODO resume here
		// Open the cost img
        RandomAccessibleInterval costRai = N5Utils.open(n5, costDataset);
        RandomAccessibleInterval originalVolumeRai = N5Utils.open(n5, volumeDataset);
        RandomAccessibleInterval flatVolumeRai = N5Utils.open(n5, volumeDataset);

        long[] dimensions = new long[3];
        originalVolumeRai.dimensions(dimensions);

        Source flatVolumeRaiSource = new RandomAccessibleSource(flatVolumeRai, flatVolumeRai, new UnsignedByteType(), "FlatVolume");
        Source originalVolumeRaiSource = new RandomAccessibleSource(originalVolumeRai, originalVolumeRai, new UnsignedByteType(), "OriginalVolume");

        ProgressWriterIJ progress = new ProgressWriterIJ();

		BigWarp.BigWarpData bwData = BigWarpInit.createBigWarpData(new Source[]{flatVolumeRaiSource},
                                                                new Source[]{originalVolumeRaiSource},
                                                                new String[]{"Flat", "Original"});

        final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc - 2)));

		BigWarp bw;
		bw = new BigWarp( bwData, n5.getBasePath(), progress );

		bw.setImagej(imagej);

		bw.setIsMovingDisplayTransformed(true);

		bw.setFullSizeInterval(Intervals.createMinMax(0, 0, 0, dimensions[0], dimensions[1], dimensions[2]));
		bw.setSourceCostImg(costRai);
		bw.restimateTransformation();
		bw.setNumScales(1);
		bw.setRawMipmaps(null);
		bw.setUseVolatile(useVolatile);
		bw.setQueue(queue);

//
//		/*
//		 * raw data
//		 */
//		final int numProc = Runtime.getRuntime().availableProcessors();
//		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc - 2)));
//
//		long padding = 2000;
//
//		final long[] dimensions = n5.getDatasetAttributes(volumeDataset + "/s0").getDimensions();
//
//		long originalDimX = dimensions[0];
//		long originalDimZ = dimensions[2];
//
//		OpService ops = imagej.op();
//
//		final RandomAccessibleInterval<DoubleType> costRai = Converters.convert((RandomAccessibleInterval<RealType>)sourceCostImg, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
//		//RandomAccessibleInterval<RealType> costImg = imagej.op().copy().rai(sourceCostImg);
//
//		double minY, maxY;
//
//		//final RandomAccessibleInterval<IntType> maxUnsignedShorts = getScaledSurfaceMap(getTopImg(costImg, ops), costImg.dimension(2)/2, originalDimX, originalDimZ, ops);
//		Pair<RandomAccessibleInterval<IntType>, DoubleType> maxPair = getScaledSurfaceMapAndAverage(getTopImg(costRai, ops), costRai.dimension(2) / 2, originalDimX, originalDimZ, ops);
//		final RandomAccessibleInterval<IntType> maxUnsignedShorts = maxPair.getA();
//		maxY = maxPair.getB().getRealDouble();
//		System.out.println("Done with top surface");
//
//		//final RandomAccessibleInterval<IntType> minUnsignedShorts = getScaledSurfaceMap(getBotImg(costImg, ops), 0, originalDimX, originalDimZ, ops);
//		Pair<RandomAccessibleInterval<IntType>, DoubleType> minPair = getScaledSurfaceMapAndAverage(getBotImg(costRai, ops), 0, originalDimX, originalDimZ, ops);
//		final RandomAccessibleInterval<IntType> minUnsignedShorts = minPair.getA();
//		minY = minPair.getB().getRealDouble();
//		System.out.println("Done with bottom surface");
//
//		// max/min should be a part of the BigWarp
//		final RandomAccessibleInterval<DoubleType> max = Converters.convert(maxUnsignedShorts, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
//		final RandomAccessibleInterval<DoubleType> min = Converters.convert(minUnsignedShorts, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
//
//		System.out.println("minY is " +  minY + " and maxY is " + maxY);
//
//		final FinalInterval cropInterval = new FinalInterval(
//				new long[] {0, 0, Math.round(minY) - padding},
//				new long[] {dimensions[0] - 1, dimensions[2] - 1, Math.round(maxY) + padding});
//
//		final int numScales = n5.list(datasetName).length;
//
//		@SuppressWarnings("unchecked")
//		final RandomAccessibleInterval<UnsignedByteType>[] rawMipmaps = new RandomAccessibleInterval[numScales];
//
//		@SuppressWarnings("unchecked")
//		final RandomAccessibleInterval<UnsignedByteType>[] mipmapsFlat = new RandomAccessibleInterval[numScales];
//		@SuppressWarnings("unchecked")
//		final RandomAccessibleInterval<UnsignedByteType>[] mipmapsOriginal = new RandomAccessibleInterval[numScales];
//
//		final double[][] scales = new double[numScales][];
//
//		/*
//		 * raw pixels for mipmap level
//		 * can be reused when transformation updates
//		 */
//		for (int s = 0; s < numScales; ++s) {
//
//			/* TODO read downsamplingFactors */
//			final int scale = 1 << s;
//			final double inverseScale = 1.0 / scale;
//
//			rawMipmaps[s] =
//					N5Utils.openVolatile(
//							n5,
//							datasetName + "/s" + s);
//		}
//
//
//		BdvStackSource<?> bdvFlat = null;
//		BdvStackSource<?> bdvOriginal = null;
//
//		/*
//		 * transform, everything below needs update when transform changes
//		 * FIXME: remember to not use a cache for the flattened source
//		 */
//		for (int s = 0; s < numScales; ++s) {
//
//			/* TODO read downsamplingFactors */
//			final int scale = 1 << s;
//			final double inverseScale = 1.0 / scale;
//
//			final RealTransformSequence transformSequenceFlat = new RealTransformSequence();
//			final Scale3D scale3D = new Scale3D(inverseScale, inverseScale, inverseScale);
//			final Translation3D shift = new Translation3D(0.5 * (scale - 1), 0.5 * (scale - 1), 0.5 * (scale - 1));
////			transformSequenceFlat.add(shift);
////			transformSequenceFlat.add(ft.inverse());
////			transformSequenceFlat.add(shift.inverse());
////			transformSequenceFlat.add(scale3D);
//
//			final RandomAccessibleInterval<UnsignedByteType> flatSource =
//					Transform.createTransformedInterval(
//							Views.permute(rawMipmaps[s], 1, 2),
//							cropInterval,
//							scale3D,
//							//transformSequenceFlat,
//							new UnsignedByteType(0));
//			final RandomAccessibleInterval<UnsignedByteType> originalSource =
//					Transform.createTransformedInterval(
//							Views.permute(rawMipmaps[s], 1, 2),
//							cropInterval,
//							scale3D,
//							new UnsignedByteType(0));
//
//			final SubsampleIntervalView<UnsignedByteType> subsampledFlatSource = Views.subsample(flatSource, scale);
//			final RandomAccessibleInterval<UnsignedByteType> cachedFlatSource = Show.wrapAsVolatileCachedCellImg(subsampledFlatSource, new int[]{32, 32, 32});
//
//			final SubsampleIntervalView<UnsignedByteType> subsampledOriginalSource = Views.subsample(originalSource, scale);
//			final RandomAccessibleInterval<UnsignedByteType> cachedOriginalSource = Show.wrapAsVolatileCachedCellImg(subsampledOriginalSource, new int[]{32, 32, 32});
//
//			if( useVolatile ) {
//				mipmapsFlat[s] = cachedFlatSource;
//				mipmapsOriginal[s] = cachedOriginalSource;
//			} else {
//				mipmapsFlat[s] = subsampledFlatSource;
//				mipmapsOriginal[s] = subsampledOriginalSource;
//			}
//			scales[s] = new double[]{scale, scale, scale};
//		}
//
//		/*
//		 * update when transforms change
//		 */
//		final RandomAccessibleIntervalMipmapSource<?> mipmapSourceFlat =
//				new RandomAccessibleIntervalMipmapSource<>(
//						mipmapsFlat,
//						new UnsignedByteType(),
//						scales,
//						voxelDimensions,
//						datasetName);
//		final RandomAccessibleIntervalMipmapSource<?> mipmapSourceOriginal =
//				new RandomAccessibleIntervalMipmapSource<>(
//						mipmapsOriginal,
//						new UnsignedByteType(),
//						scales,
//						voxelDimensions,
//						datasetName);
//
//		final Source<?> volatileMipmapSourceFlat;
//		final Source<?> volatileMipmapSourceOriginal;
//		if (useVolatile) {
//			volatileMipmapSourceFlat = mipmapSourceFlat.asVolatile(queue);
//			volatileMipmapSourceOriginal = mipmapSourceOriginal.asVolatile(queue);
//		} else {
//			volatileMipmapSourceFlat = mipmapSourceFlat;
//			volatileMipmapSourceOriginal = mipmapSourceOriginal;
//		}
//
//		ProgressWriterIJ progress = new ProgressWriterIJ();
//
//		BigWarpData bwData = BigWarpInit.createBigWarpData(new Source[]{volatileMipmapSourceFlat},
//				new Source[]{volatileMipmapSourceOriginal},
//				new String[]{"Flat", "Original"});
//
//		BigWarp bw;
//		bw = new BigWarp( bwData, new File( rawN5 ).getName(), progress );
//
//		bw.setImagej(imagej);
//
//		bw.setIsMovingDisplayTransformed(true);
//
//		bw.setFullSizeInterval(Intervals.createMinMax(0, 0, 0, dimensions[0], dimensions[1], dimensions[2]));
//		bw.setSourceCostImg(costRai);
//		bw.restimateTransformation();
//		bw.setNumScales(numScales);
//		bw.setRawMipmaps(rawMipmaps);
//		bw.setUseVolatile(useVolatile);
//		bw.setQueue(queue);

		//bw.setTransformationMovingSourceOnly(ft);

//		if ( !fnLandmarks.isEmpty() )
//			bw.getLandmarkPanel().getTableModel().load( new File( fnLandmarks ) );


	}
}
