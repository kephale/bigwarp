package bigwarp;

import bdv.ij.util.ProgressWriterIJ;
import bdv.util.RandomAccessibleIntervalSource;
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
        RandomAccessibleInterval<UnsignedByteType> costRai = N5Utils.open(n5, costDataset);
        RandomAccessibleInterval originalVolumeRai = N5Utils.open(n5, volumeDataset);
        RandomAccessibleInterval flatVolumeRai = N5Utils.open(n5, volumeDataset);

        long[] dimensions = new long[3];
        originalVolumeRai.dimensions(dimensions);

        Source flatVolumeRaiSource = new RandomAccessibleIntervalSource(flatVolumeRai, new UnsignedByteType(), "FlatVolume");
        Source originalVolumeRaiSource = new RandomAccessibleIntervalSource(originalVolumeRai, new UnsignedByteType(), "OriginalVolume");

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

		final RandomAccessibleInterval<DoubleType> costRaiDouble = Converters.convert(costRai, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());

		bw.setSourceCostImg(costRaiDouble);
		bw.restimateTransformation();
		bw.setNumScales(1);
		bw.setRawMipmaps(null);
		bw.setUseVolatile(useVolatile);
		bw.setQueue(queue);

	}
}
