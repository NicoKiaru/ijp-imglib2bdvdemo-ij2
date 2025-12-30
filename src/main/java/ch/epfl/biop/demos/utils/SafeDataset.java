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

    public static SourceAndConverter<?>[] getMacro() {

        // Not working:
        // https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0062A/6001240.zarr
        // https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0062A/6001240.zarr/0
        N5ImageData< ? > n5ImageData = new N5ImageData<>("s3://ome-zarr-scivis/v0.5/96x2/shockwave.ome.zarr");//https://uk1s3.embassy.ebi.ac.uk/idr/zarr/v0.4/idr0062A/6001240.zarr");s3://janelia-cosem-datasets/jrc_mus-hippocampus-2/jrc_mus-hippocampus-2.zarr/recon-1/em/fibsem-uint8" );
        return new SourceAndConverter[]{n5ImageData.getSourcesAndConverters().get(0)};
    }
}
