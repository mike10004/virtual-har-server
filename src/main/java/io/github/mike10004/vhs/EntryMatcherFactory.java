package io.github.mike10004.vhs;

import java.io.IOException;
import java.util.List;

public interface EntryMatcherFactory {

    <E> EntryMatcher createEntryMatcher(List<E> harEntries, RequestParser<E> requestParser) throws IOException;

}
