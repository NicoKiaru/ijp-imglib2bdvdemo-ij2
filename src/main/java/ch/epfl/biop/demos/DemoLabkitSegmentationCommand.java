package ch.epfl.biop.demos;/*-
 * #%L
 * Labkit Segmentation Demo for BigDataViewer-Playground - BIOP - EPFL
 * %%
 * Copyright (C) 2024 - 2025 EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import bdv.util.BdvHandle;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitClassifyCommand;
import org.scijava.command.CommandService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;
import sc.fiji.bdvpg.scijava.service.SourceBdvDisplayService;
import sc.fiji.bdvpg.scijava.service.SourceService;
import sc.fiji.bdvpg.viewer.bdv.navigate.ViewerTransformAdjuster;

import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * Demo showing how to apply a pre-trained Labkit classifier to SourceAndConverter sources
 * and visualize the lazy-computed segmentation results in BigDataViewer.
 * <p>
 * This demo assumes you have already trained a classifier using Labkit (see DemoLabkitIntegrationCommand).
 * The classifier is applied to create a new segmented source that computes on-demand.
 * </p>
 */
@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Labkit Segmentation")
        }
)
public class DemoLabkitSegmentationCommand implements BdvPlaygroundActionCommand {

    @Parameter
    CommandService cs;

    @Parameter
    SourceService sourceService;

    @Parameter
    SourceBdvDisplayService displayService;

    @Override
    public void run() {
        try {
            // Download a sample LLS7 (Lattice Light Sheet 7) dataset from Zenodo.
            File fileCZI = DatasetHelper
                    .getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

            // Load the CZI file using the Bio-Formats opener command.
            String datasetName = fileCZI.getName();
            cs.run(DatasetFromBioFormatsCreateCommand.class, true,
                    "datasetname", datasetName,
                    "unit", "MICROMETER",
                    "files", new File[]{fileCZI},
                    "split_rgb_channels", false,
                    "auto_pyramidize", true,
                    "plane_origin_convention", "CENTER",
                    "disable_memo", false
            ).get();

            // Apply a pre-trained Labkit classifier to the sources.
            // This creates a new source with lazy-computed segmentation labels.
            File classifierFile = new File(
                    DemoLabkitSegmentationCommand.class.getResource("/lls7-nuc-bg.classifier").getPath()
            );

            SourceAndConverter<?> classifiedSource = (SourceAndConverter<?>) cs.run(
                    SourcesLabkitClassifyCommand.class, true,
                    "sources", datasetName,
                    "classifier_file", classifierFile,
                    "resolution_level", 0,
                    "suffix", "_classified",
                    "use_gpu", true
            ).get().getOutput("source_out");

            // Show both the original sources and the classified result in BDV.
            SourceAndConverter<?>[] originalSources = sourceService.tree()
                    .getSources(datasetName)
                    .toArray(new SourceAndConverter[0]);

            displayService.show(originalSources);
            displayService.show(classifiedSource);

            // Configure display range for the classification labels (typically 0-N classes)
            sourceService.getConverterSetup(classifiedSource).setDisplayRange(0, 5);

            // Adjust view to fit the classified source
            BdvHandle bdvHandle = displayService.getActiveBdv();
            new ViewerTransformAdjuster(bdvHandle, classifiedSource).run();

        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}