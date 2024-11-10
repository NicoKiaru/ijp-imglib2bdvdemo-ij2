package ch.epfl.biop.demos;

import bdv.cache.SharedQueue;
import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.demos.utils.GameOfLifeSource;
import net.imglib2.FinalInterval;
import net.imglib2.display.LinearRange;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Game Of Life")
public class DemoGameOfLifeCommand implements Command {

    @Override
    public void run() {

        BdvHandle bdvh = SourceAndConverterServices.getBdvDisplayService().getNewBdv();

        FunctionRandomAccessible<UnsignedByteType> random =
                new FunctionRandomAccessible<>(3,
                        (position, pixel) -> {
                            pixel.set((Math.random() > 0.5)?16:0);
                        }, UnsignedByteType::new);

        SharedQueue queue = new SharedQueue(
                Runtime.getRuntime().availableProcessors()-1
        );

        SourceAndConverter<UnsignedByteType> gol = GameOfLifeSource.getSourceAndConverter(queue,
                Views.interval(random, FinalInterval.createMinMax(0,0,0,511,511,1)));

        ((LinearRange) gol.getConverter()).setMax(20);
        ((LinearRange) gol.asVolatile().getConverter()).setMax(20);

        SourceAndConverterServices
                .getBdvDisplayService()
                .show(bdvh, gol);

        new ViewerTransformAdjuster(bdvh, gol).run();

        bdvh.getViewerPanel().state().setNumTimepoints(1500);

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
