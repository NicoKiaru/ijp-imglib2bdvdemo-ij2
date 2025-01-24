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
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
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
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import sc.fiji.bdvpg.cache.GlobalLoaderCache;

/**
 * Game Of Life Source
 */
public class GameOfLifeSourcev2 implements Source<UnsignedShortType> {

    protected final DefaultInterpolators< UnsignedShortType > interpolators = new DefaultInterpolators<>();

    final String name;

    final int nResolutionLevels = 1;

    final RandomAccessibleInterval<UnsignedShortType> seed;

    final CachedCellImg img;

    final int maxStep;

    public GameOfLifeSourcev2(String name, RandomAccessibleInterval<UnsignedShortType> seed, int maxStep) {
        this.name = name;
        this.seed = seed;
        this.maxStep = maxStep;
        this.img = buildSource();
    }

    @Override
    public boolean isPresent(int t) {
        return (t==0);
    }

    private static int tileSize() {
        return 128;
    }

    private CachedCellImg getSource() {
        return img;
    }

    private CachedCellImg buildSource() {
        int tileSizeLevel = tileSize();
        final int[] cellDimensions = new int[]{ tileSizeLevel, tileSizeLevel, 1 };
        long[] newDimensions = new long[3];
        newDimensions[0] = seed.dimensionsAsLongArray()[0];
        newDimensions[1] = seed.dimensionsAsLongArray()[1];
        newDimensions[2] = maxStep;
        CellGrid grid = new CellGrid(newDimensions, cellDimensions);

        LoadedCellCacheLoader<UnsignedShortType, ?> loader = LoadedCellCacheLoader.get(grid, cell -> {
            long targetZLocation = ((Interval)cell).min(2);

            Cursor<UnsignedShortType> target = Views.flatIterable(cell).cursor();

            if (targetZLocation == 0) {
                // Copy the seed
                Cursor<UnsignedShortType> source = Views.flatIterable(Views.interval(seed, cell)).cursor();
                while (source.hasNext()) {
                    target.next().set(source.next().get());
                }
                return;
            }

            // Cursor on output image
            RandomAccess<UnsignedShortType> ra = Views.expandBorder(getSource(),1,1,0).randomAccess();

            while (target.hasNext()) {
                UnsignedShortType pixel = target.next();
                long xp = target.getLongPosition(0);
                long yp = target.getLongPosition(1);
                long zp = target.getLongPosition(2);

                ra.setPosition(new long[]{xp,yp,zp-1});
                int v11 = ra.get().get();
                int val = 0;
                ra.setPosition(new long[]{xp-1,yp-1,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp,yp-1,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp+1,yp-1,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp+1,yp,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp+1,yp+1,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp,yp+1,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp-1,yp+1,zp-1});
                val+=ra.get().get();
                ra.setPosition(new long[]{xp-1,yp,zp-1});
                val+=ra.get().get();

                if (v11==16) {
                    pixel.set((val==32)||(val==48)?16:0);
                } else {
                    pixel.set(val==48?16:0);
                }

            }

        }, new UnsignedShortType(), AccessFlags.setOf(AccessFlags.VOLATILE));
        Cache<Long, Cell<UnsignedShortType>> cache = (new GlobalLoaderCache(this, 0, 0)).withLoader(loader);
        return new CachedCellImg(grid, getType(), cache, ArrayDataAccessFactory.get(getType(), AccessFlags.setOf(AccessFlags.VOLATILE)));
    }

    @Override
    public synchronized RandomAccessibleInterval<UnsignedShortType> getSource(int t, int level) {
        if (t!=0) throw new RuntimeException("Game of life source has only one time point");
        if (level>0) throw new RuntimeException("Game of life source is not multiresolution");
        return img;
    }

    @Override
    public RealRandomAccessible<UnsignedShortType> getInterpolatedSource(int t, int level, Interpolation method) {
        return Views.interpolate( Views.extendZero(getSource( t, level )), interpolators.get(method) );
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        transform.identity();
    }

    @Override
    public UnsignedShortType getType() {
        return new UnsignedShortType();
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

    public static SourceAndConverter<UnsignedShortType> getSourceAndConverter(SharedQueue queue, RandomAccessibleInterval<UnsignedShortType> rai, int maxSteps) {

        Source<UnsignedShortType> gameOfLife = new GameOfLifeSourcev2("Game of life", rai, maxSteps);
        SourceAndConverter<UnsignedShortType> sac_out;

        SourceAndConverter<?> vsac; Source<?> vsrcRsampled;

        vsrcRsampled = new WrapVolatileSource<>(gameOfLife, queue);
        Converter< ?, ARGBType> volatileConverter = BigDataViewer.createConverterToARGB((NumericType) vsrcRsampled.getType());
        Converter< ?, ARGBType> converter = BigDataViewer.createConverterToARGB(new UnsignedByteType());

        vsac = new SourceAndConverter(vsrcRsampled,volatileConverter);
        sac_out = new SourceAndConverter(gameOfLife, converter,vsac);

        return sac_out;
    }

}
