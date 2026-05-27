package ch.epfl.biop.docs;

import java.io.File;

/**
 * Shared constants for the two Labkit-demo videos:
 * {@link GenerateLabkitClassifierDocAssets} writes
 * {@link #CLASSIFIER_FILE}, {@link GenerateLabkitApplyDocAssets} reads it.
 *
 * <p>{@link #CLASSIFIER_FILE} defaults to
 * {@code <user.dir>/labkit-cyto-nuc.classifier}; override with
 * {@code -Dlabkit.classifier.file=...}.</p>
 */
final class LabkitDoc {

    static final String DATASET_URL =
            "https://zenodo.org/records/14203207/files/Hela-Kyoto-1-Timepoint-LLS7.czi";

    static final File CLASSIFIER_FILE = new File(
            System.getProperty("labkit.classifier.file",
                    new File(System.getProperty("user.dir"), "labkit-cyto-nuc.classifier")
                            .getAbsolutePath()));

    private LabkitDoc() {}
}