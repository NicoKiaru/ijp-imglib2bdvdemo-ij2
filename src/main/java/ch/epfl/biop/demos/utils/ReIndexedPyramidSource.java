package ch.epfl.biop.demos.utils;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;

import java.util.List;

public class ReIndexedPyramidSource<T>  implements Source<T> {

    final Source<T> origin;
    final List<Integer> tgtToSrc;

    public ReIndexedPyramidSource(Source<T> origin, List<Integer> tgtToSrc) {
        this.origin = origin;
        this.tgtToSrc = tgtToSrc;
    }


    @Override
    public boolean isPresent(int t) {
        return origin.isPresent(t);
    }

    @Override
    public RandomAccessibleInterval<T> getSource(int t, int level) {
        return origin.getSource(t, tgtToSrc.get(level));
    }

    @Override
    public RealRandomAccessible<T> getInterpolatedSource(int t, int level, Interpolation method) {
        return origin.getInterpolatedSource(t, tgtToSrc.get(level), method);
    }

    @Override
    public void getSourceTransform(int t, int level, AffineTransform3D transform) {
        origin.getSourceTransform(t, tgtToSrc.get(level), transform);
    }

    @Override
    public T getType() {
        return origin.getType();
    }

    @Override
    public String getName() {
        return origin.getName();
    }

    @Override
    public VoxelDimensions getVoxelDimensions() {
        return origin.getVoxelDimensions(); // Could be wrong!!
    }

    @Override
    public int getNumMipmapLevels() {
        return tgtToSrc.size();
    }
}
