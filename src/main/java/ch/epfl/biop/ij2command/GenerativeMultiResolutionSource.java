package ch.epfl.biop.ij2command;

import bdv.util.DefaultInterpolators;
import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class GenerativeMultiResolutionSource implements Source<UnsignedShortType> {

    final String name;
    final int nLevels;
    final VoxelDimensions voxD = new FinalVoxelDimensions("pixel", 1,1,1);
    final double scaleFactor;

    transient final RandomAccessibleInterval<UnsignedShortType> rai;

    public GenerativeMultiResolutionSource(int nLevels, String name) {
        this.name = name;
        this.nLevels = nLevels;
        scaleFactor = Math.pow(2,nLevels);

        RandomAccessible<UnsignedShortType> ra = new FunctionRandomAccessible<>(3,
                (l, t) -> t.set((int) (((l.getLongPosition(0)/40L+l.getLongPosition(1)/40L+l.getLongPosition(2)/40L) % 2L)*50000))
                , UnsignedShortType::new);

        this.rai = Views.interval(ra, new FinalInterval((long)scaleFactor,(long)scaleFactor,(long)scaleFactor));
    }

    @Override
    public boolean isPresent(int t) {
        return true;
    }

    @Override
    public RandomAccessibleInterval<UnsignedShortType> getSource(int t, int level) {
        return rai;
    }

    transient protected final DefaultInterpolators< UnsignedShortType > interpolators = new DefaultInterpolators<>();

    @Override
    public RealRandomAccessible<UnsignedShortType> getInterpolatedSource(int t, int level, Interpolation method) {
        ExtendedRandomAccessibleInterval<UnsignedShortType, RandomAccessibleInterval< UnsignedShortType >>
                eView = Views.extendZero(getSource( t, level ));
        return Views.interpolate( eView, interpolators.get(method) );
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        transform.identity();
        transform.scale(Math.pow(2,level)/scaleFactor);
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
        return voxD;
    }

    @Override
    public int getNumMipmapLevels() {
        return nLevels;
    }
}
