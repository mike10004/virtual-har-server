package io.github.mike10004.vhs;

import edu.umass.cs.benchlab.har.HarEntry;
import edu.umass.cs.benchlab.har.tools.HarFileReader;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface HeuristicFactory {

    default Heuristic createHeuristic(File harFile) throws IOException {
        return createHeuristic(new HarFileReader().readHarFile(harFile).getEntries().getEntries());
    }

    Heuristic createHeuristic(List<HarEntry> harEntries);

}
