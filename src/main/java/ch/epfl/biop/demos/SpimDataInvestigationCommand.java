package ch.epfl.biop.demos;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - SpimData Investigation")
public class SpimDataInvestigationCommand implements Command {

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterService source_service;

    @Override
    public void run() {
        try {
            File wsiBrainSlices2 = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_02.vsi");
            File wsiBrainSlices3 = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");

            String datasetName = "MouseBrainSlices";

            // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                    true,
                    "datasetname", datasetName,
                    "unit", "MICROMETER",
                    "files", new File[]{wsiBrainSlices2, wsiBrainSlices3},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "TOP LEFT",
                    "auto_pyramidize", true,
                    "disable_memo", false
            ).get().getOutput("spimdata");

            SourceAndConverterServiceUI.Node datasetNode = source_service.getUI().getRoot().child(datasetName);

            // The sources from the dataset are automatically sorted according to the following properties:
            // Channel
            // ImageName
            // SeriesIndex
            // Tile

            // The dataset node points to all sources of the dataset:
            SourceAndConverter<?>[] sourcesInDataset = datasetNode.sources();
            System.out.println("There are "+sourcesInDataset.length+" sources in this dataset.");

            // If one wants to investigate the number of channels in the dataset:
            SourceAndConverterServiceUI.Node channelNodes = datasetNode.child("Channel");
            System.out.println("There are "+channelNodes.children().size()+" channels in this dataset.");

            // If one wants to know the name of the first channel:
            System.out.println("The first channel is named "+channelNodes.child(1).name());

            // Here's how to print all channel names:
            channelNodes.children().forEach( channel -> System.out.println(channel.name()));

            // Now let's check how many sources are present in the channel 1:
            SourceAndConverter<?>[] sourcesInFirstChannel = channelNodes.child(1).sources();
            System.out.println("There are "+sourcesInFirstChannel.length+" sources in the first channel of this dataset.");

            // Some other examples:
            System.out.println("There are "+datasetNode.child("ImageName").children().size()+" Images in this dataset.");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
