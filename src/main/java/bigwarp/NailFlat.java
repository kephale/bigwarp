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

import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.RandomAccessibleIntervalMipmapSource;
import bdv.util.volatiles.SharedQueue;
import bdv.viewer.Source;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.*;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
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

/**
 *
 *
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 */
public class NailFlat implements Callable<Void> {

	/* parameterize with picocli */
	private double minY = 1189.986083984375;
	private double maxY = 4233.8876953125;

	private long padding = 2000;

	//@Option(names = {"--minFaceFile"}, required = false, description = "HDF5 file with min face, e.g. --minFaceFile /nrs/flyem/alignment/Z1217-19m/VNC/Sec04/Sec04-bottom.h5")
	private String minFaceFile = "/groups/cardona/home/harringtonk/nrs_flyem/alignment/Z1217-19m/VNC/Sec04/Sec04-bottom.h5";

	private String maxFaceFile = "/groups/cardona/home/harringtonk/nrs_flyem/alignment/Z1217-19m/VNC/Sec04/Sec04-top.h5";
	private String rawN5 = "/groups/cardona/home/harringtonk/nrs_flyem/render/n5/Z1217_19m/Sec04/stacks";
	private String datasetName = "/v1_1_affine_filtered_1_26365___20191217_153959";

	private double transformScaleX = 1;
	private double transformScaleY = 1;

	private boolean useVolatile = true;

	FinalVoxelDimensions voxelDimensions = new FinalVoxelDimensions("px", new double[]{1, 1, 1});


	public static final void main(final String... args) throws IOException, InterruptedException, ExecutionException {

		//CommandLine.call(new NailFlat(), args);
		new NailFlat().call();
	}


	@Override
	public final Void call() throws IOException, InterruptedException, ExecutionException {

		/*
		 * transformation
		 */
		final IHDF5Reader hdf5ReaderMax = HDF5Factory.openForReading(maxFaceFile);
		final IHDF5Reader hdf5ReaderMin = HDF5Factory.openForReading(minFaceFile);
//		final IHDF5Reader hdf5ReaderTop = HDF5Factory.openForReading("/home/saalfeld/projects/flyem/Sec04-top.h5");
//		final IHDF5Reader hdf5ReaderBot = HDF5Factory.openForReading("/home/saalfeld/projects/flyem/Sec04-bottom.h5");
		final N5HDF5Reader hdf5Max = new N5HDF5Reader(hdf5ReaderMax, new int[] {128, 128, 128});
		final N5HDF5Reader hdf5Min = new N5HDF5Reader(hdf5ReaderMin, new int[] {128, 128, 128});

		System.out.println(Arrays.toString(hdf5Max.listAttributes("/").keySet().toArray()));
		System.out.println(hdf5Max.getDatasetAttributes("/volume").getDataType());

		final RandomAccessibleInterval<FloatType> maxFloats = N5Utils.openVolatile(hdf5Max, "/volume");
		final RandomAccessibleInterval<FloatType> minFloats = N5Utils.openVolatile(hdf5Min, "/volume");

		final RandomAccessibleInterval<DoubleType> max = Converters.convert(maxFloats, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());
		final RandomAccessibleInterval<DoubleType> min = Converters.convert(minFloats, (a, b) -> b.setReal(a.getRealDouble()), new DoubleType());

		final Scale2D transformScale = new Scale2D(transformScaleX, transformScaleY);

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
				minY,
				maxY);

		/*
		 * raw data
		 */
		final int numProc = Runtime.getRuntime().availableProcessors();
		final SharedQueue queue = new SharedQueue(Math.min(8, Math.max(1, numProc - 2)));

		final N5FSReader n5 = new N5FSReader(rawN5);

		final long[] dimensions = n5.getDatasetAttributes(datasetName + "/s0").getDimensions();

		final FinalInterval cropInterval = new FinalInterval(
				new long[] {0, 0, Math.round(minY) - padding},
				new long[] {dimensions[0] - 1, dimensions[2] - 1, Math.round(maxY) + padding});

		final int numScales = n5.list(datasetName).length;

		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] rawMipmaps = new RandomAccessibleInterval[numScales];

		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] mipmapsFlat = new RandomAccessibleInterval[numScales];
		@SuppressWarnings("unchecked")
		final RandomAccessibleInterval<UnsignedByteType>[] mipmapsOriginal = new RandomAccessibleInterval[numScales];

		final double[][] scales = new double[numScales][];

		/*
		 * raw pixels for mipmap level
		 * can be reused when transformation updates
		 */
		for (int s = 0; s < numScales; ++s) {

			/* TODO read downsamplingFactors */
			final int scale = 1 << s;
			final double inverseScale = 1.0 / scale;

			rawMipmaps[s] =
					N5Utils.openVolatile(
							n5,
							datasetName + "/s" + s);
		}


		BdvStackSource<?> bdvFlat = null;
		BdvStackSource<?> bdvOriginal = null;

		/*
		 * transform, everything below needs update when transform changes
		 */
		for (int s = 0; s < numScales; ++s) {

			/* TODO read downsamplingFactors */
			final int scale = 1 << s;
			final double inverseScale = 1.0 / scale;

			final RealTransformSequence transformSequenceFlat = new RealTransformSequence();
			final Scale3D scale3D = new Scale3D(inverseScale, inverseScale, inverseScale);
			final Translation3D shift = new Translation3D(0.5 * (scale - 1), 0.5 * (scale - 1), 0.5 * (scale - 1));
			transformSequenceFlat.add(shift);
			transformSequenceFlat.add(ft.inverse());
			transformSequenceFlat.add(shift.inverse());
			transformSequenceFlat.add(scale3D);

			final RandomAccessibleInterval<UnsignedByteType> flatSource =
					Transform.createTransformedInterval(
							Views.permute(rawMipmaps[s], 1, 2),
							cropInterval,
							transformSequenceFlat,
							new UnsignedByteType(0));
			final RandomAccessibleInterval<UnsignedByteType> originalSource =
					Transform.createTransformedInterval(
							Views.permute(rawMipmaps[s], 1, 2),
							cropInterval,
							scale3D,
							new UnsignedByteType(0));

			final SubsampleIntervalView<UnsignedByteType> subsampledFlatSource = Views.subsample(flatSource, scale);
			final RandomAccessibleInterval<UnsignedByteType> cachedFlatSource = Show.wrapAsVolatileCachedCellImg(subsampledFlatSource, new int[]{32, 32, 32});

			final SubsampleIntervalView<UnsignedByteType> subsampledOriginalSource = Views.subsample(originalSource, scale);
			final RandomAccessibleInterval<UnsignedByteType> cachedOriginalSource = Show.wrapAsVolatileCachedCellImg(subsampledOriginalSource, new int[]{32, 32, 32});

			mipmapsFlat[s] = cachedFlatSource;
			mipmapsOriginal[s] = cachedOriginalSource;
			scales[s] = new double[]{scale, scale, scale};
		}

		/*
		 * update when transforms change
		 */
		final RandomAccessibleIntervalMipmapSource<?> mipmapSourceFlat =
				new RandomAccessibleIntervalMipmapSource<>(
						mipmapsFlat,
						new UnsignedByteType(),
						scales,
						voxelDimensions,
						datasetName);
		final RandomAccessibleIntervalMipmapSource<?> mipmapSourceOriginal =
				new RandomAccessibleIntervalMipmapSource<>(
						mipmapsOriginal,
						new UnsignedByteType(),
						scales,
						voxelDimensions,
						datasetName);

		final Source<?> volatileMipmapSourceFlat;
		final Source<?> volatileMipmapSourceOriginal;
		if (useVolatile) {
			volatileMipmapSourceFlat = mipmapSourceFlat.asVolatile(queue);
			volatileMipmapSourceOriginal = mipmapSourceOriginal.asVolatile(queue);
		} else {
			volatileMipmapSourceFlat = mipmapSourceFlat;
			volatileMipmapSourceOriginal = mipmapSourceOriginal;
		}

		bdvFlat = Show.mipmapSource(volatileMipmapSourceFlat, bdvFlat, BdvOptions.options().screenScales(new double[] {0.5}).numRenderingThreads(10).frameTitle("Flattened"));
		bdvOriginal = Show.mipmapSource(volatileMipmapSourceOriginal, bdvOriginal, BdvOptions.options().screenScales(new double[] {0.5}).numRenderingThreads(10).frameTitle("Original"));



//		BdvFunctions.show(VolatileViews.wrapAsVolatile(topFloats, queue), "", BdvOptions.options().is2D());


//		BdvFunctions.show(
//				VolatileViews.wrapAsVolatile(
//						imgXZY,
//						queue),
//				"img",
//				BdvOptions.options().addTo(bdv));

		return null;
	}
}
