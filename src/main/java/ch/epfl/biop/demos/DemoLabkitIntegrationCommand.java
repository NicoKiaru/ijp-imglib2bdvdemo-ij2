package ch.epfl.biop.demos;/*-
 * #%L
 * Labkit Integration Demo for BigDataViewer-Playground - BIOP - EPFL
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

import ch.epfl.biop.DatasetHelper;
import ch.epfl.biop.bdv.img.bioformats.command.DatasetFromBioFormatsCreateCommand;
import ch.epfl.biop.command.process.labkit.SourcesLabkitOpenCommand;
import org.scijava.command.CommandService;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.command.BdvPlaygroundActionCommand;
import sc.fiji.bdvpg.scijava.BdvPgMenus;

import java.io.File;
import java.util.concurrent.ExecutionException;

/**
 * Demo showing how to use Labkit with SourceAndConverter from BigDataViewer-Playground.
 * <p>
 * This demo loads an LLS7 dataset and opens it in Labkit for segmentation.
 * </p>
 */
@SuppressWarnings({"CanBeFinal", "unused"})
@Plugin(type = BdvPlaygroundActionCommand.class,
        menu = {
                @Menu(label = BdvPgMenus.L1),
                @Menu(label = BdvPgMenus.L2),
                @Menu(label = "Demos", weight = 10),
                @Menu(label = "Demo - Labkit Integration")
        }
)
public class DemoLabkitIntegrationCommand implements BdvPlaygroundActionCommand {

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        try {
            // Download a sample LLS7 (Lattice Light Sheet 7) dataset from Zenodo.
            // This dataset contains multi-channel Hela-Kyoto cells.
            // In your own workflow, you would use your local CZI files instead.
            File fileCZI = DatasetHelper
                    .getDataset("https://zenodo.org/records/14505724/files/Hela-Kyoto-1-Timepoint-LLS7.czi");

            // Load the CZI file using the Bio-Formats opener command.
            // This command performs live deskewing of the lattice light sheet data, if you use the Zeiss Quick Start Reader
            // and registers the sources in BigDataViewer-Playground.
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

            // Open Labkit with the input image for interactive segmentation.
            // You can now use Labkit's tools to create labels and train classifiers.
            cs.run(SourcesLabkitOpenCommand.class, true,
                    "sources", datasetName,
                    "resolution_level", 0
            ).get();

        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}