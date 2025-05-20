package ch.epfl.biop.demos.utils;


import bdv.viewer.SourceAndConverter;
import org.embl.mobie.io.imagedata.N5ImageData;

/**
 * Load dataset but throw an error if the dependency is not there
 */
public class SafeDataset {
    public static SourceAndConverter<?>[] getPlaty() {
        N5ImageData< ? > n5ImageData = new N5ImageData<>( "https://s3.embl.de/i2k-2020/platy-raw.ome.zarr" );
        return new SourceAndConverter[]{n5ImageData.getSourcesAndConverters().get(0)};
    }
}
