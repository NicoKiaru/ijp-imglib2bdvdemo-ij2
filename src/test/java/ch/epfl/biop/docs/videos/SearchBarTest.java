package ch.epfl.biop.docs.videos;

/**
 * Drives Fiji's legacy search bar via Robot to run "show bdv play" —
 * opens the BigDataViewer-Playground tree view.
 */
public class SearchBarTest {

    public static void main(String[] args) {
        FijiCommands.startFiji();
        FijiCommands.searchAndRun("show bdv play");
    }
}