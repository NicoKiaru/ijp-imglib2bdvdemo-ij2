package ch.epfl.biop.ij2command;

import bdv.tools.brightness.ConverterSetup;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class)
public class ShiftConverterSetupSlider extends InteractiveCommand {

    @Parameter
    ConverterSetup converter;

    @Parameter
    double min;

    @Parameter
    double max;

    @Parameter(persist = false, style = "slider", min = "0", max = "1", stepSize = "0.01")
    double ratiolevel = 0.5;

    @Parameter
    double width = 1;

    public void run() {
        double level = min + ratiolevel*(max-min);
        converter.setDisplayRange(level-width/2.0, level + width/2.0);
    }

}
