package ch.epfl.biop.demos;

import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.scijava.services.ui.SourceAndConverterServiceUI;

import java.io.File;
import java.util.List;
import java.util.Map;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - SpimData Manipulation")
public class SpimDataManipulationExampleCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String description = "<html> <h1>SpimData Manipulation Example</h1>\n" +
            "    <p>This demo illustrates how to manipulate <code>SpimData</code> objects using BigDataViewer (BDV) and related libraries. <code>SpimData</code> objects encapsulate image data and metadata, allowing for comprehensive data management and visualization.</p>\n" +
            "\n" +
            "    <h2>Key Steps in the Demo:</h2>\n" +
            "\n" +
            "    <h3>1. Loading the Dataset</h3>\n" +
            "    <p>A sample dataset is loaded from a specified file path. The dataset is encapsulated within an <code>AbstractSpimData</code> object, which contains both the pixel data and metadata.</p>\n" +
            "\n" +
            "    <h3>2. Exploring Dataset Metadata</h3>\n" +
            "    <p>The demo explores the structure of the dataset by accessing information about views, setups, and channels. This involves navigating through a hierarchy that includes view IDs, descriptions, and metadata about individual setups.</p>\n" +
            "\n" +
            "    <h3>3. Accessing Spatial Information</h3>\n" +
            "    <p>The spatial positioning of the dataset is accessed using the <code>ViewRegistrations</code> object. This object provides affine transformations that describe how each view is positioned in 3D space.</p>\n" +
            "\n" +
            "    <h3>4. Handling SourceAndConverter Objects</h3>\n" +
            "    <p><code>SourceAndConverter</code> objects are utilized to access and manipulate pixel data. These objects are essentially immutable in BDV, but this demo demonstrates how to extract and inspect pixel data using their methods.</p>\n" +
            "\n" +
            "    <h3>5. Traversing the Data Tree</h3>\n" +
            "    <p>The demo showcases how to navigate the tree structure of the <code>SourceAndConverterServiceUI</code>, which organizes data hierarchically and allows for easy traversal and access to different parts of the dataset.</p>\n" +
            "\n" +
            "    <p>This example highlights the capabilities of BDV in manipulating and visualizing complex imaging datasets, providing insights into hierarchical data structures and spatial transformations.</p>\n" +
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
            File wsiBrainSlices = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");

            // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                    true,
                    "datasetname", "Egg_Chamber",
                    "unit", "MICROMETER",
                    "files", new File[]{wsiBrainSlices},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "TOP LEFT",
                    "auto_pyramidize", true,
                    "disable_memo", false
            ).get().getOutput("spimdata");

            // A SpimData object, or AbstractSpimData object, is the natural bdv data structure.
            // It holds raw pixel data and metadata.
            // Here's a breakdowm.
            // Basically a SpimData contains 'Setups', which are 4D dataset (XYZT), located in space thanks
            // to a chain of Affine Transforms.
            // Setups also have Attributes, like Channel(S), Tile(s), etc.

            // One can retrieve the sequence description object:
            AbstractSequenceDescription<?, ?, ?> seqDescription = dataset.getSequenceDescription();

            // It links a ViewId to View Description
            Map<ViewId, ? extends BasicViewDescription> viewDescription = seqDescription.getViewDescriptions();

            // A ViewId serves to identify an XYZ dataset, indexed by a setup id and a timepoint
            ViewId vid = new ViewId(0, 1); // First argument = timepoint, second argument = Setup Id

            // Let's have a look at the BasicViewDescription of this ViewId

            BasicViewDescription exampleVD = viewDescription.get(vid);

            BasicViewSetup viewSetup = exampleVD.getViewSetup();

            // One can finally get the metadata of this viewsetup
            // The name:
            String viewSetupName = viewSetup.getName();

            // The Channel Attribute:
            Channel channel = viewSetup.getAttribute(Channel.class);

            // The channel has a name;
            String channelName = channel.getName();

            // And a int identifier:
            int channelIdx = channel.getId();

            // There are some other attributes which can be accessed :
            Map<String, Entity> attributes = viewSetup.getAttributes();

            // Now let's have a look at how the viewsetups are positioned in 3D along the time:
            ViewRegistrations viewRegistrations = dataset.getViewRegistrations();

            // One can ask the information for a specific ViewId:
            ViewRegistration reg = viewRegistrations.getViewRegistration(vid);
            AffineTransform3D transform = reg.getModel(); // This is a 4x3 Affine transforms which positions the 3D pixel data in space - it is actually the result of a list of successive affine transforms

            List<ViewTransform> transformList = reg.getTransformList();

            // Let's have a look at the first one:

            ViewTransform viewTransform = transformList.get(0);
            String viewTransformName = viewTransform.getName(); // This transform can have a name or be null
            AffineGet affineTransform = viewTransform.asAffine3D(); // Why it is not an AffineTransform3D, I have no idea

            // SourceAndConverter objects are objects generated from the SpimData that can be used to access spatial information and pixel data
            // But, they are essentially not modifiable.
            // In BigDataViewer Playground, the pixel data are essentially immutable
            // It is possible to change the spatial location of the SourceAndConverter object, but this has to be done
            // through the underlying SpimData object. How to do it is not detailed now. You thus can't do it.

            // But the SourceAndConverterService provides an easy way to access the SourceAndConverter from SpimData objects
            // here's various way to achieve that:

            List<SourceAndConverter<?>> sourceAndConverterFromSpimdata = source_service.getSourceAndConverterFromSpimdata(dataset);

            // However the SourceAndConverters are not easily sorted through properties such as all sources of a certain Channel,
            // or all sources of a Tile.

            // To do that the SourceAndConverter UI can be convenient:

            SourceAndConverterServiceUI.Node root = source_service.getUI().getRoot();

            // All sources put in the SourceAndConverter service are a tree structure. The root of the tree contains all sources.

            SourceAndConverter<?>[] allSources = root.sources(); // These are all sources contained within the SourceAndConverter service

            // To access all the sources of the Spimdata dataset object one can either use;

            List<SourceAndConverter<?>> sourcesOfDataset = source_service.getSourceAndConverterFromSpimdata(dataset);

            // But one can also use the tree structure and refer to the datasetname that was given when opening the file (here "Egg_Chamber")

            SourceAndConverter<?>[] alsoSourcesOfDataset = root.child("Egg_Chamber").sources();

            // To get all the sources of the first channel, one can use:

            SourceAndConverter<?>[] sourcesOfChannel1 = root.child("Egg_Chamber").child("Channel").child(channelName).sources();

            // All right. Now how to access raw pixel data of a SourceAndConverter object ?

            Source<?> source = sourcesOfChannel1[0].getSpimSource(); // 1st, get the source

            int timepoint = 0; // First timepoint
            int resolutionLevel = 0; //  resolution level 0 = highest resolution
            RandomAccessibleInterval<?> rai = source.getSource(timepoint,  resolutionLevel);

            // The rai variable is a random accessible interval of dimension 3

            // The dimensions of the rai can be found like that:

            long nPixX = rai.dimension(0); // 0 = X
            long nPixY = rai.dimension(1); // 1 = Y
            long nPixZ = rai.dimension(2); // 2 = Z

            int px = 0; int py = 1; int pz = 10;

            Object pixel = rai.getAt(px, py, pz); // The pixel can be of any type, but generally it will be a RealType or a ARGBtype

            // If you really need to get the pixel data, then here's a way:

            if (pixel instanceof RealType) {
                RealType<?> castPixel = (RealType) pixel;
                double pixelValue = castPixel.getRealDouble();
            } else if (pixel instanceof ARGBType) {
                ARGBType castPixel = (ARGBType) pixel;
                int pixelValue = castPixel.get();
            }

            // Note: a Node object from the UI can be looked at like that:

            for (SourceAndConverterServiceUI.Node node : root.children()) {
                System.out.println(node.name());
                System.out.println(node.child(0)); // Children nodes can be indexed
                node.sources(); // Gets the array of sources below the node
                String path = node.path(); // Path of the node, where each node is separated with a > character
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
