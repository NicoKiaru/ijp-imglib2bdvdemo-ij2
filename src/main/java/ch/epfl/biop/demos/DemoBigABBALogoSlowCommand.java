package ch.epfl.biop.demos;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = Command.class, menuPath = "Plugins>BIOP>Demos>Demo - Big ABBA Logo (Slow)")
public class DemoBigABBALogoSlowCommand implements Command {

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
            //BdvStackSource<UnsignedShortType> bss = BdvFunctions.show(allSources.get(0), 1, BdvOptions.options().is2D());
            SerializableBdvOptions opts = new SerializableBdvOptions();
            opts.is2D = true;
            BdvHandle bdvh = new DefaultBdvSupplier(opts).get();
            //ds.show(bdvh, allSources.toArray(new SourceAndConverter[0]));
            allSources.forEach(source -> BdvFunctions.show(source, BdvOptions.options().addTo(bdvh)));
            new ViewerTransformAdjuster(bdvh, allSources.toArray(new SourceAndConverter[0])).run();

        } catch (InterruptedException | ExecutionException | IOException e) {
            System.err.println(e.getMessage());
            log.error(e);
        }
    }

    public static File getResourceAsFile(String resourcePath) throws IOException {
        try (InputStream in = DemoBigABBALogoSlowCommand.class.getResource(resourcePath).openStream()){

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

}
