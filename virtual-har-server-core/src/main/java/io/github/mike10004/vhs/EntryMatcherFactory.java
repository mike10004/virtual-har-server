package io.github.mike10004.vhs;

import java.io.IOException;
import java.util.List;

public interface EntryMatcherFactory {

    <E> EntryMatcher createEntryMatcher(List<E> harEntries, EntryParser<E> requestParser) throws IOException;

}
