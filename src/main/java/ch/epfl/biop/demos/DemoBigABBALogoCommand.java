package ch.epfl.biop.demos;

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.img.bioformats.command.CreateBdvDatasetBioFormatsCommand;
import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import mpicbg.spim.data.generic.AbstractSpimData;
import net.imglib2.RealPoint;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.LinearRange;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.bdv.navigate.ViewerTransformAdjuster;
import sc.fiji.bdvpg.bdv.supplier.DefaultBdvSupplier;
import sc.fiji.bdvpg.bdv.supplier.SerializableBdvOptions;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterBdvDisplayService;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.sourceandconverter.SourceAndConverterHelper;
import sc.fiji.bdvpg.sourceandconverter.transform.SourceAffineTransformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Big ABBA Logo")
public class DemoBigABBALogoCommand implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String demodescription = "" +
            "<html>" +
            "<h1>The ABBA Logo with Brain Slices Demo</h1>\n" +
           // "    <p><img src='"+getResource("images/DemoBigABBA.png")+"' width='100' height='100'></img></p>" +
            "    <p>This demos shows the responsiveness of BigDataViewer even when many images are displayed at once.In brief, thousands of brain slice images are displayed in the shape of a ABBA logo. " +
            "    Here's a breakdown of what the demo accomplishes:</p>\n" +
            "\n" +
            "    <h2>1. Loading Large Datasets</h2>\n" +
            "    <p>The demo begins by downloading and loading large VSI files representing brain slices (cached, the first download can be long). These files are loaded into a <code>SpimData</code> object, which holds the metadata and pixel data.</p>\n" +
            "\n" +
            "    <h2>2. Filtering and Preparing</h2>\n" +
            "    <p>The demo filters the datasets to keep only sources containing \"DAPI,\" in their names. It prepares a list of these filtered sources for further processing.</p>\n" +
            "\n" +
            "    <h2>3. Processing the ABBA Logo</h2>\n" +
            "    <p>An image of the ABBA logo is loaded and resized to 70x70 pixels.</p>\n" +
            "\n" +
            "    <h2>4. Creating a Grid of Logos</h2>\n" +
            "    <p>The demo places a brain slice image for each pixel location of the logo. Each \"pixel\" is transformed with scaling, rotation, and translation .</p>\n" +
            "\n" +
            "    <h2>5. Displaying the Result</h2>\n" +
            "    <p>All of this is displayed using BDV. The demo sets up a 2D viewer and adjusts the viewer's transform to ensure BDV's view spans all the logo.</p>\n" +
            "\n" +
            "    <p>This demo showcases the power and flexibility of ImgLib2 and BDV in visualizing and manipulating large image datasets, displaying at once 5k big 2D multiresolution images.</p>" +
            "    <br> Note that you can always explore the source code of the demo by clicking the <code>source</code> button.</br>" +
            "</html>\n";

    @Parameter() // Its role is to make sure that the description is displayed
    boolean ok;

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterBdvDisplayService ds;

    @Parameter
    SourceAndConverterService ss;

    @Parameter
    LogService log;

    @Override
    public void run() {
        try {
            // Downloads and cache a sample  vsi file (1.3Gb) from https://zenodo.org/records/6553641
            File wsiBrainSlices3 = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(3), "Slide_03.vsi");
            File wsiBrainSlices4 = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(4), "Slide_04.vsi");
            File wsiBrainSlices5 = new File(ch.epfl.biop.DatasetHelper.dowloadBrainVSIDataset(5), "Slide_05.vsi");

            // Retrieve the dataset, that's a SpimData object, it holds metadata and the 'recipe' to load pixel data
            AbstractSpimData<?> dataset = (AbstractSpimData<?>) cs.run(CreateBdvDatasetBioFormatsCommand.class,
                    true,
                    "datasetname", "Egg_Chamber",
                    "unit", "MICROMETER",
                    "files", new File[]{wsiBrainSlices3, wsiBrainSlices4, wsiBrainSlices5},
                    "split_rgb_channels", false,
                    "plane_origin_convention", "TOP LEFT",
                    "auto_pyramidize", true,
                    "disable_memo", false
            ).get().getOutput("spimdata");

            SourceAndConverter<?>[] brainSlicesSources = ss.getSourceAndConverterFromSpimdata(dataset)
                    .toArray(new SourceAndConverter<?>[0]);

            List<SourceAndConverter<?>> sources = Arrays.asList(brainSlicesSources).stream()
                    .filter(src -> src.getSpimSource().getName().contains("DAPI"))
                    .collect(Collectors.toList());

            int nSources = sources.size();

            List<SourceAndConverter<UnsignedShortType>> allSources = new ArrayList<>();

            int iSource = 0;

            ImagePlus imp = IJ.openImage(getResourceAsFile("/graphics/ABBAFrame.jpg").getAbsolutePath());

            imp = imp.resize(70,70,"");

            ColorProcessor p = (ColorProcessor) imp.getProcessor();

            for (int x = 0; x<imp.getWidth(); x++) {
                for (int y = 0; y<imp.getHeight(); y++) {
                    SourceAndConverter<?> source = sources.get(iSource % nSources);
                    AffineTransform3D location = new AffineTransform3D();
                    source.getSpimSource().getSourceTransform(0,0, location);
                    RealPoint center = SourceAndConverterHelper.getSourceAndConverterCenterPoint(source, 0);
                    long nPixX = source.getSpimSource().getSource(0, 0).dimension(0);
                    long nPixY = source.getSpimSource().getSource(0, 0).dimension(1);
                    long nPixZ = source.getSpimSource().getSource(0, 0).dimension(2);
                    AffineTransform3D transform = new AffineTransform3D();
                    transform.preConcatenate(location.inverse());
                    double cy = y - imp.getHeight() / 2.0;
                    double cx = x - imp.getHeight() / 2.0;
                    double scaleFactor = 1.5 * (Math.cos(Math.sqrt(cx * cx + cy * cy) / 35.0));
                    scaleFactor = scaleFactor*scaleFactor;
                    double sx = (0.8 / (double) nPixX) * scaleFactor;
                    double sy = ((1 / (double) nPixY) * 1.5) * scaleFactor;
                    transform.scale(sx, sy, 1 / (double) nPixZ);
                    transform.translate(-sx / 2.0, -sy / 2.0, 0);
                    transform.rotate(2, Math.atan2(cy, cx) + Math.PI / 2.0);
                    transform.translate(x, y, 0);
                    SourceAndConverter<?> trSource = new SourceAffineTransformer<>(source, transform).get();
                    ((LinearRange) trSource.asVolatile().getConverter()).setMin(0);
                    ((LinearRange) trSource.asVolatile().getConverter()).setMax(1000);
                    ((LinearRange) trSource.getConverter()).setMin(0);
                    ((LinearRange) trSource.getConverter()).setMax(1000);

                    ARGBType color = new ARGBType(
                            ARGBType.rgba(
                                    p.getColor(x, y).getRed(),
                                    p.getColor(x, y).getGreen(),
                                    p.getColor(x, y).getBlue(), 255));
                    ((ColorConverter) trSource.asVolatile().getConverter()).setColor(color);
                    ((ColorConverter) trSource.getConverter()).setColor(color);
                    allSources.add((SourceAndConverter<UnsignedShortType>) trSource);
                    iSource++;
                }
            }
            bdv.util.Prefs.showMultibox(false);
            SerializableBdvOptions opts = new SerializableBdvOptions();
            opts.is2D = true;
            BdvHandle bdvh = new DefaultBdvSupplier(opts).get();
            /*BdvHandle bdvh;
            AlphaSerializableBdvOptions optsAlpha = new AlphaSerializableBdvOptions();
            optsAlpha.is2D = true;
            optsAlpha.white_bg = true;
            bdvh = new AlphaBdvSupplier(optsAlpha).get();*/
            ds.show(bdvh, allSources.toArray(new SourceAndConverter[0]));
            new ViewerTransformAdjuster(bdvh, allSources.toArray(new SourceAndConverter[0])).run();

        } catch (InterruptedException | ExecutionException | IOException e) {
            System.err.println(e.getMessage());
            log.error(e);
        }
    }

    public static File getResourceAsFile(String resourcePath) throws IOException {
        try (InputStream in = DemoBigABBALogoCommand.class.getResource(resourcePath).openStream()){

            if (in == null) {
                throw new IOException("Could not get BIOP logo from jar file");
            }

            File tempFile = File.createTempFile(String.valueOf(in.hashCode()), ".tmp");
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                //copy stream
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected URL getResource(final String name) {
        return DemoBigABBALogoCommand.class.getClassLoader().getResource(name);
    }

}
