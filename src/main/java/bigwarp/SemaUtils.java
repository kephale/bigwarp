package bigwarp;

import ij.ImagePlus;
import ij.plugin.FolderOpener;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

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
}
