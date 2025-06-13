package ch.epfl.biop.demos;

import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;

import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - SpimData Investigation")
public class SpimDataInvestigationCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>Investigating SpimData</h1>\n" +
            "    <p>This demo illustrates how to investigate a SpimData dataset using ImgLib2 and BigDataViewer (BDV). SpimData is a data format used in the BigDataViewer for handling large microscopy images and their metadata.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Dataset</h3>\n" +
            "    <p>The demo begins by loading a SpimData dataset from specified VSI files. These files represent brain slices and are loaded into a <code>SpimData</code> object, which holds metadata and the recipe to load pixel data.</p>\n" +
            "\n" +
            "    <h3>2. Accessing the Dataset</h3>\n" +
            "    <p>The loaded dataset is accessed through a node structure provided by the <code>SourceAndConverterServiceUI</code>. This allows for easy navigation and investigation of different components of the dataset.</p>\n" +
            "\n" +
            "    <h3>3. Exploring Dataset Properties</h3>\n" +
            "    <p>The demo explores various properties of the dataset, such as the number of sources, channels, and image names. These properties are accessed and printed to provide an overview of the dataset's structure.</p>\n" +
            "\n" +
            "    <h3>4. Printing Channel Names</h3>\n" +
            "    <p>The demo prints out the names of all channels in the dataset, which helps in identifying and analyzing different components of the dataset.</p>\n" +
            "\n" +
            "    <h3>5. Investigating Specific Channels</h3>\n" +
            "    <p>The number of sources present in a specific channel is determined and printed. This aids in understanding the distribution of data across different channels.</p>\n" +
            "\n" +
            "    <h3>6. Error Handling</h3>\n" +
            "    <p>The demo includes error handling to manage potential issues during the loading and investigation of the dataset.</p>\n" +
            "\n" +
            "    <p>This demo showcases the capabilities of ImgLib2 and BDV in handling and investigating large microscopy datasets, providing researchers with tools to explore and analyze complex image data.</p>\n" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>";

    @Parameter // Its role is to make sure that the description is displayed
    boolean ok;


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
