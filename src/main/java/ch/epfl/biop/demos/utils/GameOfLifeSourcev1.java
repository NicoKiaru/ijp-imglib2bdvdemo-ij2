package ch.epfl.biop.demos.utils;

import bdv.BigDataViewer;
import bdv.cache.SharedQueue;
import bdv.util.DefaultInterpolators;
import bdv.util.WrapVolatileSource;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.cache.Cache;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.cache.img.LoadedCellCacheLoader;
import net.imglib2.converter.Converter;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.ArrayDataAccessFactory;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

import java.util.HashMap;
import java.util.Map;

/**
 * Game Of Life Source
 */
public class GameOfLifeSourcev1 implements Source<UnsignedByteType> {

    protected final DefaultInterpolators< UnsignedByteType > interpolators = new DefaultInterpolators<>();

    final String name;

    final int nResolutionLevels;

    Map<Integer, RandomAccessibleInterval<UnsignedByteType>> sources = new HashMap<>();

    public GameOfLifeSourcev1(String name, RandomAccessibleInterval<UnsignedByteType> origin) {
        this.name = name;
        sources.put(-1, origin); // I have to put it at t=-1 because it can't be 'wrapped as volatile'
        this.nResolutionLevels = 1;
    }

    @Override
    public boolean isPresent(int t) {
        return (t>=0)&&(t<5000);
    }

    private static int tileSize() {
        return 64;
    }

    private void buildSources(int t) {
        int tileSizeLevel = tileSize();
        final int[] cellDimensions = new int[]{ tileSizeLevel, tileSizeLevel, 1 };

        if (!sources.containsKey(t-1)) buildSources(t-1);

        final RandomAccessibleInterval<UnsignedByteType> raiBelow = sources.get(t-1);
        long[] newDimensions = new long[3];
        newDimensions[0] = raiBelow.dimensionsAsLongArray()[0];
        newDimensions[1] = raiBelow.dimensionsAsLongArray()[1];
        newDimensions[2] = raiBelow.dimensionsAsLongArray()[2];
        CellGrid grid = new CellGrid(newDimensions, cellDimensions);

        final RandomAccessibleInterval<UnsignedByteType> rai00 = Views.expandBorder(raiBelow,1,1,0);

        LoadedCellCacheLoader<UnsignedByteType, ?> loader = LoadedCellCacheLoader.get(grid, cell -> {

            // Cursor on output image
            Cursor<UnsignedByteType> out = Views.flatIterable(cell).cursor();
            IntervalView<UnsignedByteType> iView = Views.interval(rai00, cell);
            final Cursor< UnsignedByteType > center = Views.flatIterable(iView).cursor();

            final RectangleShape shape = new RectangleShape( 1, true );

            for ( final Neighborhood< UnsignedByteType > localNeighborhood : shape.neighborhoods( Views.hyperSlice(iView,2,0) ) ) {
                int val = 0;
                for ( final UnsignedByteType value : localNeighborhood ) {
                    val += value.get();
                }
                if (center.next().get()==16) {
                    out.next().set((val==32)||(val==48)?16:0);
                } else {
                    out.next().set(val==48?16:0);
                }
            }

        }, new UnsignedByteType(), AccessFlags.setOf(AccessFlags.VOLATILE));
        Cache<Long, Cell<UnsignedByteType>> cache = (new GlobalLoaderCache(this, t, 0)).withLoader(loader);
        CachedCellImg img = new CachedCellImg(grid, getType(), cache, ArrayDataAccessFactory.get(getType(), AccessFlags.setOf(AccessFlags.VOLATILE)));
        sources.put(t, img);
    }

    @Override
    public synchronized RandomAccessibleInterval<UnsignedByteType> getSource(int t, int level) {
        if (!sources.containsKey(t)) {
            buildSources(t);
        }
        if (level>0) throw new RuntimeException("Game of life source is not multiresolution");
        return sources.get(t);
    }

    @Override
    public RealRandomAccessible<UnsignedByteType> getInterpolatedSource(int t, int level, Interpolation method) {
        return Views.interpolate( Views.extendZero(getSource( t, level )), interpolators.get(method) );
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        transform.identity();
    }

    @Override
    public UnsignedByteType getType() {
        return new UnsignedByteType();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return new FinalVoxelDimensions("pixel", 1,1,1);
    }

    @Override
    public int getNumMipmapLevels() {
        return nResolutionLevels;
    }

    public static SourceAndConverter<UnsignedByteType> getSourceAndConverter(SharedQueue queue, RandomAccessibleInterval<UnsignedByteType> rai) {

        Source<UnsignedByteType> gameOfLife = new GameOfLifeSourcev1("Game of life", rai);

        SourceAndConverter<UnsignedByteType> sac_out;

        SourceAndConverter<?> vsac;
        Source<?> vsrcRsampled;

        vsrcRsampled = new WrapVolatileSource<>(gameOfLife, queue);

        Converter< ?, ARGBType> volatileConverter = BigDataViewer.createConverterToARGB((NumericType) vsrcRsampled.getType());
        Converter< ?, ARGBType> converter = BigDataViewer.createConverterToARGB(new UnsignedByteType());


        vsac = new SourceAndConverter(vsrcRsampled,volatileConverter);
        sac_out = new SourceAndConverter(gameOfLife, converter,vsac);

        return sac_out;
    }

}
