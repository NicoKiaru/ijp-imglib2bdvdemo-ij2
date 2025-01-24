package ch.epfl.biop.test;

import io.scif.img.IO;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.util.Arrays;

public class DebugForum {

    public static < T extends RealType< T > & NativeType< T >> void main(String[] args )
    {
        ImageJ ij = new ImageJ();
        ij.launch( args );

        final Img< T > largeImage = ( Img< T > ) IO.openAll( "C:/Users/Nicolas/Desktop/Game of life-1.tif" ).get( 0 ).getImg();
        ImageJFunctions.show( largeImage, "original" );
        System.out.println(Arrays.toString(largeImage.dimensionsAsLongArray()));
        for ( int i = 0; i < largeImage.dimension( largeImage.numDimensions() - 1 ); i++ ) // iterates along the z axis
        {
            final IntervalView< T > inputInterval = Views.hyperSlice( largeImage, largeImage.numDimensions() - 1, i );
            System.out.println("plane "+i+Arrays.toString(inputInterval.dimensionsAsLongArray()));
            System.out.println(inputInterval.firstElement());
        }
    }
}
