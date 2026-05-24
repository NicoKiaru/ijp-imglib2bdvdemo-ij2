package ch.epfl.biop.docs.videos.core;

import java.awt.Frame;

/**
 * Grid-based placement for AWT {@link Frame}s relative to the target screen.
 *
 * <p>A {@code Layout} is a rectangular area on the target screen (offset
 * relative to the screen origin, plus width × height). Each {@link #place}
 * call specifies its <em>own</em> {@code rows × cols} partition of that same
 * area and places the frame at one cell — optionally spanning several rows or
 * columns. Different calls can re-partition the same area independently, so a
 * window can take the full area in one phase and shrink to a single column in
 * the next without the {@code Layout} holding any per-window state.</p>
 *
 * <p>Pixel rectangle for {@code (rows, cols, row, col, rowSpan, colSpan)}:
 * {@code (dx + col*W/cols, dy + row*H/rows, colSpan*W/cols, rowSpan*H/rows)}.
 * Placement goes through {@link Ui#resizeFrame} + {@link Ui#dragFrame}, so the
 * resize-then-drag gestures read on video the same as a manual layout pass.</p>
 *
 * <p>Offsets are measured relative to the target screen (same convention as
 * {@link Ui#placeFrame(Frame, int, int)} / {@link Ui#dragFrame}). The area is
 * not clipped against the screen — a too-large area will produce out-of-screen
 * coordinates and the frame's window manager will clamp.</p>
 */
public class Layout {

    private final int dx, dy, width, height;

    private Layout(int dx, int dy, int width, int height) {
        this.dx = dx;
        this.dy = dy;
        this.width = width;
        this.height = height;
    }

    /**
     * Define a layout area at {@code (dx, dy)} of size {@code width × height},
     * with the offset measured relative to the target screen origin.
     */
    public static Layout area(int dx, int dy, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width / height must be > 0, got "
                    + width + " / " + height);
        }
        return new Layout(dx, dy, width, height);
    }

    /**
     * Place {@code frame} at a single grid cell. Equivalent to
     * {@link #place(Frame, int, int, int, int, int, int)} with
     * {@code rowSpan = colSpan = 1}.
     */
    public void place(Frame frame, int rows, int cols, int row, int col) {
        place(frame, rows, cols, row, col, 1, 1);
    }

    /**
     * Place {@code frame} at the cell {@code (row, col)} of an implicit
     * {@code rows × cols} partition of the area, spanning {@code rowSpan} rows
     * and {@code colSpan} columns. Visibly resizes then drags.
     */
    public void place(Frame frame, int rows, int cols, int row, int col,
                      int rowSpan, int colSpan) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("rows / cols must be > 0, got "
                    + rows + " / " + cols);
        }
        if (rowSpan <= 0 || colSpan <= 0) {
            throw new IllegalArgumentException("rowSpan / colSpan must be > 0, got "
                    + rowSpan + " / " + colSpan);
        }
        if (row < 0 || col < 0 || row + rowSpan > rows || col + colSpan > cols) {
            throw new IllegalArgumentException("Cell (" + row + "," + col
                    + ") span (" + rowSpan + "," + colSpan
                    + ") does not fit in grid " + rows + "x" + cols);
        }
        int cellW = width / cols;
        int cellH = height / rows;
        int x = dx + col * cellW;
        int y = dy + row * cellH;
        int w = colSpan * cellW;
        int h = rowSpan * cellH;

        Ui.resizeFrame(frame, w, h);
        Ui.dragFrame(frame, x, y);

    }
}