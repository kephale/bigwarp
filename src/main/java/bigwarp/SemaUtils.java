package bigwarp;

import ij.ImagePlus;
import ij.plugin.FolderOpener;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

import java.util.Iterator;

public class SemaUtils {
    public static Img<RealType> readAndFlipCost(String directory) {
        ImagePlus imp = FolderOpener.open(directory, " file=tif");
        //imp.setTitle("Target");
        // Reslice image
        //IJ.run(imp, "Reslice [/]...", "output=1.000 start=Top avoid");
        //imp = IJ.getImage();

        // Reslice using imglib
        Img<RealType> img = ImageJFunctions.wrapReal(imp);
        RandomAccessibleInterval<RealType> rotView = Views.invertAxis(Views.rotate(img, 1, 2),1);
        Img<RealType> resliceImg = img.factory().create(rotView);
        Cursor<RealType> rotCur = Views.iterable(rotView).cursor();
        Cursor<RealType> resCur = resliceImg.cursor();
        while( rotCur.hasNext() ) {
            rotCur.fwd();
            resCur.fwd();
            resCur.get().set(rotCur.get());
        }
        return resliceImg;
    }

    public static DoubleType getAvgValue(RandomAccessibleInterval<IntType> rai) throws Exception {
        RandomAccess<IntType> ra = rai.randomAccess();
        long[] pos = new long[rai.numDimensions()];
        for( int k = 0; k < pos.length; k++ ) pos[k] = 0;
        ra.localize(pos);
        DoubleType avg = new DoubleType();

        long count = 0;
        DoubleType tmp = new DoubleType();
        for( pos[0] = 0; pos[0] < rai.dimension(0); pos[0]++ ) {
            for( pos[1] = 0; pos[1] < rai.dimension(1); pos[1]++ ) {
                ra.localize(pos);
                tmp.setReal(ra.get().getRealDouble());
                avg.add(tmp);
                count++;
            }
        }
        avg.div(new DoubleType(count));


        return avg;
    }

    public static DoubleType getAvgValue(Iterable<DoubleType> ii) throws Exception {
        if( !ii.iterator().hasNext() ) {
            throw new Exception("Iterable is empty");
        }
        DoubleType avg = (DoubleType) ii.iterator().next().copy();
        long count = 0;
        avg.setReal(0);
        Iterator<DoubleType> it = ii.iterator();
        DoubleType val;
        while( it.hasNext() ) {
            val = it.next();
            avg.add(val);
            count++;
        }
        avg.setReal(avg.getRealDouble()/count);
        return avg;
    }
}
