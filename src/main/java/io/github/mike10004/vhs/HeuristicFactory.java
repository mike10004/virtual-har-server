package io.github.mike10004.vhs;

import java.io.File;
import java.io.IOException;

public interface HeuristicFactory {

    Heuristic createHeuristic(File harFile) throws IOException;

}
