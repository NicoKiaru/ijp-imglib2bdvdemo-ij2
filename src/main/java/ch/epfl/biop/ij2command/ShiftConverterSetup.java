package ch.epfl.biop.ij2command;

import bdv.tools.brightness.ConverterSetup;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class)
public class ShiftConverterSetup extends InteractiveCommand {

    @Parameter
    ConverterSetup converter;

    @Parameter//(style = "slider", min = "-1", max = "8", stepSize = "0.05")
    double level = 0;

    @Parameter
    double width = 1;

    public void run() {
        converter.setDisplayRange(level-width/2.0, level + width/2.0);
    }

}
