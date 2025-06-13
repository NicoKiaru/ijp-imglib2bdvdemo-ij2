package ch.epfl.biop.demos;

import bdv.viewer.SourceAndConverter;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
import ch.epfl.biop.atlas.mouse.allen.ccfv3p1.command.AllenBrainAdultMouseAtlasCCF2017v3p1Command;
import ch.epfl.biop.atlas.struct.Atlas;
import net.imglib2.Volatile;
import net.imglib2.type.numeric.ARGBType;
import ome.xml.meta.IMetadata;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Display Allen Brain")
public class DemoDisplayAllenBrainCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String demodescription = "<html><h1>Displaying the Allen Brain Atlas</h1>\n" +
            "    <p>This demo showcases how to load and visualize the Allen Brain Atlas using ImgLib2 and BigDataViewer (BDV).</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Atlas</h3>\n" +
            "    <p>The demo begins by loading the Allen Brain Atlas using the <code>AllenBrainAdultMouseAtlasCCF2017v3p1Command</code>. This command retrieves the atlas data and stores it in an <code>Atlas</code> object.</p>\n" +
            "\n" +
            "    <h3>2. Exploring Atlas Channels</h3>\n" +
            "    <p>The demo prints out the available channels in the atlas. </p>\n" +
            "\n" +
            "    <h3>3. Selecting Channels</h3>\n" +
            "    <p>Two specific channels are selected for visualization: the Nissl stain and the anatomical reference channels. These channels are retrieved as <code>SourceAndConverter</code> objects.</p>\n" +
            "\n" +
            "    <h3>4. Displaying the Channels</h3>\n" +
            "    <p>The demo uses BigVolumeViewer through <code>BvvFunctions</code> to display these two channels.</p>\n" +
            "\n" +
            "    <h3>5. Adjusting Display Settings</h3>\n" +
            "    <p>To enhance the visualization, the demo adjusts the display settings for each channel. This includes setting the display range and color for each channel to make the visualization more visually appealing.</p>\n" +
            "\n" +
            "    <p>This demo highlights the capabilities of ImgLib2 and BDV in visualizing complex and large-scale biological datasets like the Allen Brain Atlas, providing researchers with powerful tools for exploring and anatomical structures in the mouse brain.</p>" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>\n";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;

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
