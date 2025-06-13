package ch.epfl.biop.demos;

import bdv.cache.SharedQueue;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.GameOfLifeSourcev2;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import net.imglib2.FinalInterval;
import net.imglib2.display.LinearRange;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Game Of Life")
public class DemoGameOfLifeCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Visualizing Conway's Game of Life</h1>\n" +
            "    <p>This demo illustrates how to simulate and visualize Conway's Game of Life using ImgLib2 and BigDataViewer (BDV). Conway's Game of Life is a cellular automaton devised by mathematician John Conway, which simulates the evolution of populations based on initial conditions.</p>\n" +
            "\n" +
            "    <h2>Key Components of the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Setting Up the Viewer</h3>\n" +
            "    <p>A BDV handle is created to display the Game of Life simulation. This viewer is used to render the cellular automaton state over time.</p>\n" +
            "\n" +
            "    <h3>2. Choosing the Initial Configuration</h3>\n" +
            "    <p>The initial state of the Game of Life grid can be set in two ways: randomly generated or from an input image. The choice is handled with an enumeration and conditional logic.</p>\n" +
            "\n" +
            "    <h3>3. Creating the Game of Life Simulation</h3>\n" +
            "    <p>The simulation is implemented using a <code>FunctionRandomAccessible</code> to define the initial grid state. For random initialization, each cell has a 50% chance to be alive or dead. If an image is used, the image intensity determines the initial state of each cell.</p>\n" +
            "\n" +
            "    <h3>4. Running the Simulation</h3>\n" +
            "    <p>The Game of Life rules are applied iteratively using a <code>SharedQueue</code> for concurrent processing. A <code>GameOfLifeSource</code> manages the grid state transitions over time, allowing for real-time visualization of the cellular automaton dynamics.</p>\n" +
            "\n" +
            "    <h3>5. Interactive Controls</h3>\n" +
            "    <p>This demo includes a simple control panel with pause and resume buttons to control the simulation's execution. This allows for interactive exploration of the Game of Life patterns.</p>\n" +
            "\n" +
            "    <p>This demo showcases the use of ImgLib2 and BDV to visualize dynamic systems, providing an interactive view into the evolution of cellular automata according to Conway's rules.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;

    @Parameter(choices = {"v0", "v1", "v2"})
    String implementation;

    @Parameter(choices = {"From Image", "Random"})
    String seed_choice;

    @Parameter(required = false)
    ImagePlus image_start;

    @Override
    public void run() {

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        int maxX, maxY;
        FunctionRandomAccessible<UnsignedShortType> seed;
        if (seed_choice.equals("Random")) {
            seed = new FunctionRandomAccessible<>(3,
                    (position, pixel) -> {
                        pixel.set((Math.random() > 0.5)?16:0);
                    }, UnsignedShortType::new);
            maxX = 512;
            maxY = 512;
        } else if (seed_choice.equals("From Image")) {
            if (image_start==null) {
                IJ.log("Error: no image was provided! ");
                return;
            }
            ImageProcessor ip = image_start.getProcessor();
            seed = new FunctionRandomAccessible<>(3,
                    (position, pixel) -> {
                        pixel.set((ip.get(position.getIntPosition(0), position.getIntPosition(1)) == 0)?0:16);
                    }, UnsignedShortType::new);

            maxX = ip.getWidth()-1;
            maxY = ip.getHeight()-1;
        } else {
            IJ.log("Invalid seed choice");
            return;
        }


        SharedQueue queue = new SharedQueue(
                Runtime.getRuntime().availableProcessors()-1
        );

        SourceAndConverter<UnsignedShortType> gol = GameOfLifeSourcev2.getSourceAndConverter(queue,
                Views.interval(seed, FinalInterval.createMinMax(0,0,0,maxX,maxY,1)), 500);

        ((LinearRange) gol.getConverter()).setMax(17);
        ((LinearRange) gol.asVolatile().getConverter()).setMax(17);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdvh, gol);

        //new ViewerTransformAdjuster(bdvh, gol).run();

        //bdvh.getViewerPanel().state().setNumTimepoints(1500);

        bdvh.getCardPanel().addCard("Control Computation", makePauseResumePanel(queue), true);

        bdvh.getSplitPanel().setCollapsed(false);

    }

    JPanel makePauseResumePanel(SharedQueue queue) {
        // Create a JPanel to hold the buttons
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        // Create the "Pause" button
        JButton pauseButton = new JButton("Pause");
        pauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Pause button clicked");
                // Add any additional actions for pausing here
                try {
                    queue.pause();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        // Create the "Resume" button
        JButton resumeButton = new JButton("Resume");
        resumeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Resume button clicked");
                // Add any additional actions for resuming here
                new Thread(() -> {
                    try {
                        queue.resume();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }).start();
            }
        });

        // Add buttons to the panel
        panel.add(pauseButton);
        panel.add(resumeButton);

        return panel;
    }

}
