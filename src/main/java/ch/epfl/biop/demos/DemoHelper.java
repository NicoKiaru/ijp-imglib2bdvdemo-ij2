
package ch.epfl.biop.demos;
/*-
 * #%L
 * BigDataViewer-Playground
 * %%
 * Copyright (C) 2019 - 2026 Nicolas Chiaruttini, EPFL - Robert Haase, MPI CBG - Christian Tischer, EMBL
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import com.formdev.flatlaf.FlatDarkLaf;
import net.imagej.ImageJ;
import sc.fiji.bdvpg.scijava.service.SourceService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DemoHelper {

    // ==================== SETUP METHODS ====================

    /**
     * Expands the SourceAndConverter tree view to a specified depth.
     * Call this at the beginning of demos to make screenshots more informative.
     *
     * @param ij the ImageJ instance
     * @param depth the depth to expand (typically 3)
     */
    public static void expandTreeView(ImageJ ij, int depth) {
        SourceService sourceService = ij.get(SourceService.class);
        if (sourceService != null && sourceService.tree() != null) {
            sourceService.tree().expandToDepth(depth);
        }
    }

    /**
     * Expands the SourceAndConverter tree view to depth 3 (default).
     *
     * @param ij the ImageJ instance
     */
    public static void expandTreeView(ImageJ ij) {
        expandTreeView(ij, 3);
    }

    // ==================== SEMI-INTERACTIVE METHODS ====================

    /**
     * Pauses the demo for manual user action, then captures screenshots.
     * Shows a dialog with instructions and waits for the user to click "Done".
     * After the user completes the action and clicks the button, screenshots are captured.
     *
     * <p>Example usage:</p>
     * <pre>
     * DemoHelper.pauseForUserAction("DemoLabkit_03_segmentation",
     *     "Please perform the following steps in Labkit:\n" +
     *     "1. Draw scribbles on the background (label 'background')\n" +
     *     "2. Draw scribbles on the cells (label 'foreground')\n" +
     *     "3. Click 'Train Classifier' to see the segmentation");
     *
     * // With window filtering:
     * DemoHelper.pauseForUserAction("DemoLabkit_03_segmentation",
     *     "Instructions...",
     *     "Labkit");  // Only capture Labkit windows
     * </pre>
     *
     * @param prefix prefix for screenshot filenames
     * @param instructions multi-line instructions for the user
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void pauseForUserAction(String prefix, String instructions, String... titleFilters) {
        showInstructionDialog("Manual Step Required", instructions, "Done - Capture Screenshots");
        System.out.println("[Demo] Capturing screenshots...");
        shot(prefix, titleFilters);
    }

    /**
     * Pauses the demo for manual user action with custom wait time before screenshot.
     *
     * @param prefix prefix for screenshot filenames
     * @param instructions multi-line instructions for the user
     * @param waitMs milliseconds to wait after user clicks Done before capturing
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void pauseForUserActionWithWait(String prefix, String instructions, long waitMs, String... titleFilters) {
        showInstructionDialog("Manual Step Required", instructions, "Done - Capture Screenshots");
        System.out.println("[Demo] Capturing screenshots...");
        shotWithWait(prefix, waitMs, titleFilters);
    }

    /**
     * Pauses the demo to display information without capturing screenshots.
     * Useful for explaining what will happen next.
     *
     * @param message the message to display
     */
    public static void pause(String message) {
        showInstructionDialog("Demo Paused", message, "Continue");
    }

    /**
     * Shows a non-blocking instruction dialog and waits for the user to click the button.
     *
     * @param title the dialog title
     * @param instructions the instructions to display
     * @param buttonText the text for the action button
     */
    private static void showInstructionDialog(String title, String instructions, String buttonText) {
        final CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> {
            JFrame dialog = new JFrame(title);
            dialog.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            dialog.setAlwaysOnTop(true);

            JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
            contentPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

            // Title label
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
            contentPanel.add(titleLabel, BorderLayout.NORTH);

            // Instructions text area
            JTextArea textArea = new JTextArea(instructions);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBackground(contentPanel.getBackground());
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            textArea.setBorder(new EmptyBorder(10, 0, 10, 0));

            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setBorder(null);
            scrollPane.setPreferredSize(new Dimension(450, 200));
            contentPanel.add(scrollPane, BorderLayout.CENTER);

            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            JButton doneButton = new JButton(buttonText);
            doneButton.setFont(doneButton.getFont().deriveFont(Font.BOLD, 14f));
            doneButton.setPreferredSize(new Dimension(220, 35));
            doneButton.addActionListener(e -> {
                dialog.dispose();
                latch.countDown();
            });
            buttonPanel.add(doneButton);
            contentPanel.add(buttonPanel, BorderLayout.SOUTH);

            dialog.setContentPane(contentPanel);
            dialog.pack();
            dialog.setLocationRelativeTo(null); // Center on screen
            dialog.setVisible(true);

            // Request focus on the button
            doneButton.requestFocusInWindow();
        });

        // Wait for the user to click the button
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void startFiji(ImageJ ij) {
        try {
            SwingUtilities.invokeAndWait(() -> ij.ui().showUI());
            try {
                // Increase default font size
                UIManager.put("defaultFont", new Font("SansSerif", Font.PLAIN, 16));
                FlatDarkLaf.setup();
            } catch (Exception e) {
                System.err.println("Failed to set FlatLaf look and feel: " + e.getMessage());
            }

            updateAllFramesLookAndFeel();

        } catch (InterruptedException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }


    /**
     * Updates all existing Swing frames/windows to use the current Look and Feel.
     * This is necessary when the L&F is changed after some windows were already created.
     */
    private static void updateAllFramesLookAndFeel() {
        SwingUtilities.invokeLater(() -> {
            for (Window window : Window.getWindows()) {
                SwingUtilities.updateComponentTreeUI(window);
                window.pack();
            }
        });
    }

    /** Default output directory for screenshots */
    public static final File DEFAULT_OUTPUT_DIR = new File("documentation/resources");

    /** Default wait time in milliseconds before capturing */
    public static final long DEFAULT_WAIT_MS = 4000;

    // ==================== CLIPBOARD SCREENSHOT METHODS ====================

    /**
     * Shows a dialog instructing the user to take a snip, then reads the image from the clipboard and saves it.
     *
     * <p>Typical workflow:</p>
     * <ol>
     *   <li>Dialog appears with {@code message} (e.g. "Open menu Plugins > BigDataViewer, then snip it")</li>
     *   <li>User opens the menu, presses Win+Shift+S (Snipping Tool), selects region</li>
     *   <li>User clicks OK in the dialog</li>
     *   <li>Code reads the clipboard image and saves it as {@code filename.png}</li>
     * </ol>
     *
     * <p>Example usage:</p>
     * <pre>
     * DemoHelper.shotFromClipboard(OUTPUT_DIR, "menu_open_sources",
     *     "Open menu: Plugins > BigDataViewer > Open Sources\n" +
     *     "Take a snip with Win+Shift+S, then click OK.");
     * </pre>
     *
     * @param outputDir directory to save the screenshot
     * @param filename  filename without extension (saved as {@code filename.png})
     * @param message   instructions shown to the user
     */
    public static void shotFromClipboard(File outputDir, String filename, String message) {
        showInstructionDialog("Screenshot from Clipboard",
                message + "\n\nTake your snip (Win+Shift+S), then click OK.",
                "OK – save clipboard image");

        try {
            java.awt.datatransfer.Transferable contents =
                    Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);

            if (contents == null || !contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor)) {
                System.err.println("[Screenshot] Clipboard does not contain an image. Nothing saved.");
                return;
            }

            java.awt.Image img = (java.awt.Image)
                    contents.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor);

            // Convert to BufferedImage
            BufferedImage buffered = new BufferedImage(
                    img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = buffered.createGraphics();
            g2d.drawImage(img, 0, 0, null);
            g2d.dispose();

            outputDir.mkdirs();
            File outputFile = new File(outputDir, filename + ".png");
            ImageIO.write(buffered, "png", outputFile);
            System.out.println("[Screenshot] Saved clipboard image: " + outputFile.getPath());

        } catch (Exception e) {
            System.err.println("[Screenshot] Failed to read clipboard: " + e.getMessage());
        }
    }

    /**
     * Convenience overload using {@link #DEFAULT_OUTPUT_DIR}.
     *
     * @param filename filename without extension
     * @param message  instructions shown to the user
     */
    public static void shotFromClipboard(String filename, String message) {
        shotFromClipboard(DEFAULT_OUTPUT_DIR, filename, message);
    }

    // ==================== ONE-LINER METHODS ====================

    /**
     * Captures all visible windows with a prefix.
     * Waits for rendering, captures JFrames, and prints results.
     * <p>
     * Example usage:
     * <pre>
     * DemoHelper.shot("MyDemo_01_step");                           // All windows
     * DemoHelper.shot("MyDemo_01_step", "BDV", "Labkit");          // Only BDV and Labkit windows
     * DemoHelper.shot("MyDemo_01_step", "ImageJ");                 // Only ImageJ window
     * </pre>
     *
     * @param prefix prefix for screenshot filenames (e.g., demo class name)
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured.
     *                     If empty, all visible windows are captured.
     */
    public static void shot(String prefix, String... titleFilters) {
        shot(DEFAULT_OUTPUT_DIR, prefix, DEFAULT_WAIT_MS, titleFilters);
    }

    /**
     * Captures windows with a prefix and custom wait time.
     * <p>
     * Example usage: {@code DemoHelper.shot("MyDemo", 2000, "BDV");}
     *
     * @param prefix prefix for screenshot filenames
     * @param waitMs milliseconds to wait before capturing
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void shotWithWait(String prefix, long waitMs, String... titleFilters) {
        shot(DEFAULT_OUTPUT_DIR, prefix, waitMs, titleFilters);
    }

    /**
     * Captures windows to a custom directory with filtering.
     *
     * @param outputDir directory to save screenshots
     * @param prefix prefix for screenshot filenames
     * @param waitMs milliseconds to wait before capturing
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     */
    public static void shot(File outputDir, String prefix, long waitMs, String... titleFilters) {
        try {
            waitFor(waitMs);
            List<File> files = captureAllFramesOffscreen(outputDir, prefix, titleFilters);
            String filterInfo = (titleFilters.length > 0)
                    ? " (filtered by: " + String.join(", ", titleFilters) + ")"
                    : "";
            System.out.println("[Screenshot] Captured " + files.size() + " frame(s) with prefix '" + prefix + "'" + filterInfo + ":");
            for (File f : files) {
                System.out.println("  -> " + f.getPath());
            }
        } catch (Exception e) {
            System.err.println("[Screenshot] Failed to capture: " + e.getMessage());
        }
    }

    // ==================== DETAILED METHODS ====================

    /**
     * Captures a screenshot of a specific JFrame.
     *
     * @param frame the frame to capture
     * @param outputFile the file to save the screenshot to (PNG format)
     * @throws AWTException if the platform doesn't support screen capture
     * @throws IOException if the file cannot be written
     */
    public static void captureFrame(JFrame frame, File outputFile) throws AWTException, IOException {
        // Ensure parent directories exist
        outputFile.getParentFile().mkdirs();

        // Get the frame bounds on screen
        Rectangle bounds = frame.getBounds();

        // Use Robot to capture the screen region
        Robot robot = new Robot();
        BufferedImage screenshot = robot.createScreenCapture(bounds);

        // Write to file
        ImageIO.write(screenshot, "png", outputFile);
    }

    /**
     * Captures a screenshot of a specific JFrame using component painting.
     * This method doesn't require the window to be on top and works better
     * in headless/virtual display environments.
     *
     * @param frame the frame to capture
     * @param outputFile the file to save the screenshot to (PNG format)
     * @throws IOException if the file cannot be written
     */
    public static void captureFrameOffscreen(JFrame frame, File outputFile) throws IOException {
        // Ensure parent directories exist
        outputFile.getParentFile().mkdirs();

        // Create a buffered image with the frame's size
        int width = frame.getWidth();
        int height = frame.getHeight();

        if (width <= 0 || height <= 0) {
            throw new IllegalStateException("Frame has invalid dimensions: " + width + "x" + height);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Paint the frame content
        frame.paint(g2d);
        g2d.dispose();

        // Write to file
        ImageIO.write(image, "png", outputFile);
    }

    /**
     * Gets all visible JFrames in the application.
     *
     * @return list of all visible JFrame instances
     */
    public static List<JFrame> getAllVisibleFrames() {
        return getFilteredVisibleFrames();
    }

    /**
     * Gets visible JFrames filtered by title.
     * If no filters are provided, returns all visible frames.
     *
     * @param titleFilters optional filters - only frames whose title contains one of these strings are returned
     * @return list of matching visible JFrame instances
     */
    public static List<JFrame> getFilteredVisibleFrames(String... titleFilters) {
        List<JFrame> frames = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame && window.isVisible()) {
                JFrame frame = (JFrame) window;
                if (matchesTitleFilter(frame.getTitle(), titleFilters)) {
                    frames.add(frame);
                }
            }
        }
        return frames;
    }

    /**
     * Checks if a window title matches any of the provided filters.
     *
     * @param title the window title to check
     * @param filters the filters to match against (case-insensitive, partial match)
     * @return true if filters is empty OR title contains any of the filter strings
     */
    private static boolean matchesTitleFilter(String title, String... filters) {
        if (filters == null || filters.length == 0) {
            return true; // No filters = match all
        }
        if (title == null) {
            return false;
        }
        String lowerTitle = title.toLowerCase();
        for (String filter : filters) {
            if (filter != null && lowerTitle.contains(filter.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Captures screenshots of visible JFrames and saves them to a directory.
     * Files are named based on the frame title or a generated index.
     *
     * @param outputDir the directory to save screenshots to
     * @param prefix optional prefix for filenames (can be null)
     * @param useRobot if true, uses Robot for screen capture; if false, uses offscreen painting
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     * @return list of files that were created
     * @throws AWTException if Robot capture is used and platform doesn't support it
     * @throws IOException if files cannot be written
     */
    public static List<File> captureAllFrames(File outputDir, String prefix, boolean useRobot, String... titleFilters)
            throws AWTException, IOException {
        List<File> capturedFiles = new ArrayList<>();
        List<JFrame> frames = getFilteredVisibleFrames(titleFilters);

        int index = 0;
        for (JFrame frame : frames) {
            System.out.println(frame.getTitle());
            String filename = generateFilename(frame, prefix, index);
            File outputFile = new File(outputDir, filename);

            if (useRobot) {
                captureFrame(frame, outputFile);
            } else {
                captureFrameOffscreen(frame, outputFile);
            }

            capturedFiles.add(outputFile);
            index++;
        }

        return capturedFiles;
    }

    /**
     * Captures screenshots of visible JFrames using offscreen painting.
     * This is the preferred method for CI/automated environments.
     *
     * @param outputDir the directory to save screenshots to
     * @param prefix optional prefix for filenames (can be null)
     * @param titleFilters optional filters - only windows whose title contains one of these strings will be captured
     * @return list of files that were created
     * @throws IOException if files cannot be written
     */
    public static List<File> captureAllFramesOffscreen(File outputDir, String prefix, String... titleFilters) throws IOException {
        try {
            return captureAllFrames(outputDir, prefix, false, titleFilters);
        } catch (AWTException e) {
            // This shouldn't happen with offscreen capture, but just in case
            throw new IOException("Unexpected AWTException during offscreen capture", e);
        }
    }

    /**
     * Generates a sanitized filename for a frame screenshot.
     */
    private static String generateFilename(JFrame frame, String prefix, int index) {
        String title = frame.getTitle();
        String baseName;

        if (title != null && !title.trim().isEmpty()) {
            // Sanitize the title for use as filename
            baseName = title.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            // Remove consecutive underscores
            baseName = baseName.replaceAll("_+", "_");
            // Trim underscores from start/end
            baseName = baseName.replaceAll("^_|_$", "");
        } else {
            baseName = "frame_" + index;
        }

        if (prefix != null && !prefix.isEmpty()) {
            baseName = prefix + "_" + baseName;
        }

        return baseName + ".png";
    }

    /**
     * Waits for all pending Swing events to be processed.
     * Useful to ensure windows are fully rendered before capturing.
     */
    public static void waitForSwingToSettle() {
        try {
            // Process all pending Swing events
            SwingUtilities.invokeAndWait(() -> {});
            // Small additional delay to ensure rendering is complete
            Thread.sleep(500);
        } catch (Exception e) {
            // Ignore interruption
        }
    }

    /**
     * Waits for a specific duration (in milliseconds).
     * Useful when windows need time to fully initialize.
     *
     * @param millis time to wait in milliseconds
     */
    public static void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== EDIT-IN-IMAGEJ CAPTURE ====================

    /**
     * Captures each matching window to a {@link BufferedImage}, opens it as an
     * {@code ImagePlus} in ImageJ so the user can annotate it (arrows, text,
     * overlays …), then flattens and saves the result when the user clicks Save.
     *
     * @param outputDir  directory to save the final images
     * @param prefix     filename prefix
     * @param waitMs     milliseconds to wait before capturing (for rendering)
     * @param filters    title filters passed to {@link #getFilteredVisibleFrames(String...)}
     * @return list of files that were saved
     */
    private static List<File> captureAndEditFrames(File outputDir, String prefix, long waitMs, String[] filters) {
        waitFor(waitMs);
        List<File> saved = new ArrayList<>();
        List<JFrame> frames = getFilteredVisibleFrames(filters);
        int index = 0;
        for (JFrame frame : frames) {
            try {
                // 1 – render frame to BufferedImage
                int w = frame.getWidth(), h = frame.getHeight();
                if (w <= 0 || h <= 0) {
                    System.err.println("[Screenshot] Skipping frame with invalid size: " + frame.getTitle());
                    continue;
                }
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = img.createGraphics();
                frame.paint(g2d);
                g2d.dispose();

                // 2 – open in ImageJ for annotation
                ij.ImagePlus imp = new ij.ImagePlus(frame.getTitle(), img);
                imp.show();

                showInstructionDialog("Annotate Screenshot",
                        "Annotate \"" + frame.getTitle() + "\" in the ImageJ window.\n" +
                        "Use any ImageJ tool: arrows, text, overlays, measurements…\n\n" +
                        "When done, click Save to flatten and write the image.",
                        "Save");

                // 3 – flatten overlays and save
                outputDir.mkdirs();
                File outputFile = new File(outputDir, generateFilename(frame, prefix, index));
                ij.ImagePlus flat = imp.flatten();
                ImageIO.write(flat.getBufferedImage(), "png", outputFile);
                imp.changes = false;
                imp.close();
                flat.close();

                saved.add(outputFile);
                System.out.println("[Screenshot] Saved annotated: " + outputFile.getPath());
                index++;
            } catch (Exception e) {
                System.err.println("[Screenshot] Failed for frame \"" + frame.getTitle() + "\": " + e.getMessage());
            }
        }
        return saved;
    }

    // ==================== BUILDER FACTORY METHODS ====================

    /**
     * Returns a fluent builder for capturing window screenshots.
     *
     * <p>Example – pause then capture a single BDV window:</p>
     * <pre>
     * DemoHelper.shot()
     *     .to(OUTPUT_DIR)
     *     .prefix("bdv_show_sources")
     *     .waitMs(4000)
     *     .filter("BigDataViewer")
     *     .pause("Scenario 1 – adjust the view, then click Continue.")
     *     .capture();
     * </pre>
     */
    public static ShotBuilder shot() {
        return new ShotBuilder();
    }

    /**
     * Returns a fluent builder for capturing a screenshot from the system clipboard.
     *
     * <p>Example:</p>
     * <pre>
     * DemoHelper.clipboardShot()
     *     .to(OUTPUT_DIR)
     *     .filename("menu_open_sources")
     *     .withMessage("Open Plugins > BigDataViewer > Open Sources, snip it with Win+Shift+S, then click OK.")
     *     .capture();
     * </pre>
     */
    public static ClipboardShotBuilder clipboardShot() {
        return new ClipboardShotBuilder();
    }

    // ==================== BUILDER CLASSES ====================

    /**
     * Fluent builder for window screenshot capture.
     * Defaults to {@link #DEFAULT_OUTPUT_DIR} and {@link #DEFAULT_WAIT_MS}.
     */
    public static class ShotBuilder {

        private File outputDir = DEFAULT_OUTPUT_DIR;
        private String prefix = "";
        private long waitMs = DEFAULT_WAIT_MS;
        private final List<String> filters = new ArrayList<>();
        private String pauseMessage = null;
        private boolean editInImageJ = false;

        /** Sets the directory where screenshots will be saved. */
        public ShotBuilder to(File outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /** Sets the filename prefix (e.g. {@code "bdv_show_sources"}). */
        public ShotBuilder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /** Overrides the default wait time before the capture. */
        public ShotBuilder waitMs(long waitMs) {
            this.waitMs = waitMs;
            return this;
        }

        /**
         * Adds one or more title filters: only windows whose title contains at least
         * one of the given strings (case-insensitive) will be captured.
         * Calling this method multiple times accumulates the filters.
         */
        public ShotBuilder filter(String... titleFilters) {
            filters.addAll(Arrays.asList(titleFilters));
            return this;
        }

        /**
         * Shows a "Demo Paused / Continue" dialog before capturing.
         * Replaces a separate {@link DemoHelper#pause(String)} call.
         */
        public ShotBuilder pause(String message) {
            this.pauseMessage = message;
            return this;
        }

        /**
         * Opens each captured frame in an ImageJ window so you can annotate it
         * (arrows, text, ROI overlays …) before it is saved.
         * After clicking "Save" in the dialog, the image is flattened and written to disk.
         */
        public ShotBuilder editInImageJ() {
            this.editInImageJ = true;
            return this;
        }

        /**
         * Executes the capture: shows the pause dialog (if set), waits, then
         * captures all matching windows. If {@link #editInImageJ()} was called,
         * each frame is opened in ImageJ for annotation before saving; otherwise
         * the frames are saved directly.
         */
        public void capture() {
            if (pauseMessage != null) {
                showInstructionDialog("Demo Paused", pauseMessage, "Continue");
            }
            String[] filterArray = filters.toArray(new String[0]);
            if (editInImageJ) {
                List<File> files = captureAndEditFrames(outputDir, prefix, waitMs, filterArray);
                System.out.println("[Screenshot] Saved " + files.size() + " annotated frame(s) with prefix '" + prefix + "'.");
            } else {
                shot(outputDir, prefix, waitMs, filterArray);
            }
        }
    }

    /**
     * Fluent builder for clipboard-based screenshot capture.
     * Defaults to {@link #DEFAULT_OUTPUT_DIR}.
     */
    public static class ClipboardShotBuilder {

        private File outputDir = DEFAULT_OUTPUT_DIR;
        private String filename = "screenshot";
        private String message = "Take your snip with Win+Shift+S, then click OK.";

        /** Sets the directory where the image will be saved. */
        public ClipboardShotBuilder to(File outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        /** Sets the output filename (without extension – saved as {@code filename.png}). */
        public ClipboardShotBuilder filename(String filename) {
            this.filename = filename;
            return this;
        }

        /** Sets the instruction message shown to the user before they take the snip. */
        public ClipboardShotBuilder withMessage(String message) {
            this.message = message;
            return this;
        }

        /**
         * Shows the instruction dialog, then reads the clipboard image and saves it.
         */
        public void capture() {
            shotFromClipboard(outputDir, filename, message);
        }
    }
}
