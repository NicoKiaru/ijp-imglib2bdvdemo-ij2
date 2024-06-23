package ch.epfl.biop.demos;

import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
import ch.epfl.biop.atlas.mouse.allen.ccfv3p1.command.AllenBrainAdultMouseAtlasCCF2017v3p1Command;
import ch.epfl.biop.atlas.struct.Atlas;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Display Allen Brain")
public class DisplayAllenBrainCommand implements Command {

    @Parameter
    CommandService cs;

    @Parameter
    LogService logService;

    @Override
    public void run() {
        try {
            Atlas atlas = (Atlas) cs.run(AllenBrainAdultMouseAtlasCCF2017v3p1Command.class, true).get().getOutput("ba");

            // What channels do we have in this atlas ?
            System.out.println(atlas.getMap().getImagesKeys());
            // The output will be: [Nissl, Ara, Label Borders, X, Y, Z, Left Right]

            // Let's collect the first two channels
            SourceAndConverter<?> nissl = atlas.getMap().getStructuralImages().get("Nissl");
            SourceAndConverter<?> ara = atlas.getMap().getStructuralImages().get("Ara");

            // Let's display one channel
            BvvStackSource<? extends Volatile<?>> bvvNisslStack = BvvFunctions.show(nissl.asVolatile().getSpimSource());

            // And the other in the same viewer
            BvvStackSource<? extends Volatile<?>> bvvAraStack = BvvFunctions.show(ara.asVolatile().getSpimSource(), BvvOptions.options().addTo(bvvNisslStack.getBvvHandle()));

            // Let's change the display settings to make this look nicer
            bvvNisslStack.setDisplayRange(0, 25000);
            bvvAraStack.setDisplayRange(0, 350);

            bvvNisslStack.setColor(new ARGBType(ARGBType.rgba(0, 128, 255, 0)));
            bvvAraStack.setColor(new ARGBType(ARGBType.rgba(220, 250, 0, 0)));

        } catch (Exception e) {
            logService.error(e);
        }
    }

}
