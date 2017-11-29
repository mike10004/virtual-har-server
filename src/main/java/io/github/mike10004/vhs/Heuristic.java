package io.github.mike10004.vhs;

import edu.umass.cs.benchlab.har.HarEntry;

/**
 * Class that represents
 */
public interface Heuristic {

    HarEntry findTopEntry(ParsedRequest request);

}
