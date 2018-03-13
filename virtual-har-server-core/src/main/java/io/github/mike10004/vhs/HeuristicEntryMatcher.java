package io.github.mike10004.vhs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import io.github.mike10004.vhs.harbridge.ParsedRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

public class HeuristicEntryMatcher implements EntryMatcher {

    private static final Logger log = LoggerFactory.getLogger(HeuristicEntryMatcher.class);

    private final ImmutableList<ParsedEntry> entries;
    private final Heuristic heuristic;
    private final int thresholdExclusive;

    HeuristicEntryMatcher(Heuristic heuristic, int thresholdExclusive, Collection<ParsedEntry> entries) {
        this.entries = ImmutableList.copyOf(entries);
        this.thresholdExclusive = thresholdExclusive;
        this.heuristic = requireNonNull(heuristic);
    }

    public static EntryMatcherFactory factory(Heuristic heuristic, int thresholdExclusive) {
        return new Factory(heuristic, thresholdExclusive);
    }

    /**
     * Interface that maps a request to a response.
     */
    interface HttpRespondableCreator {
        /**
         * Constructs and returns a respondable.
         * @param request the request
         * @return the response
         * @throws IOException on I/O error
         */
        HttpRespondable createRespondable(ParsedRequest request) throws IOException;
    }

    private static class Factory implements EntryMatcherFactory {

        private static final Logger log = LoggerFactory.getLogger(Factory.class);

        private final Heuristic heuristic;
        private final int thresholdExclusive;

        private Factory(Heuristic heuristic, int thresholdExclusive) {
            this.thresholdExclusive = thresholdExclusive;
            this.heuristic = heuristic;
        }

        @Override
        public <E> EntryMatcher createEntryMatcher(List<E> entries, EntryParser<E> requestParser) throws IOException {
            log.trace("constructing heuristic from {} har entries", entries.size());
            List<ParsedEntry> parsedEntries = new ArrayList<>(entries.size());
            for (E entry : entries) {
                ParsedRequest request = requestParser.parseRequest(entry);
                HttpRespondableCreator respondableCreator = newRequest -> {
                    return requestParser.parseResponse(newRequest, entry);
                };
                ParsedEntry parsedEntry = new ParsedEntry(request, respondableCreator);
                parsedEntries.add(parsedEntry);
            }
            return new HeuristicEntryMatcher(heuristic, thresholdExclusive, parsedEntries);
        }
    }

    @Override
    @Nullable
    public HttpRespondable findTopEntry(ParsedRequest request) {
        AtomicInteger topRating = new AtomicInteger(thresholdExclusive);
        @Nullable HttpRespondableCreator topEntry = entries.stream()
                .max(Ordering.<Integer>natural().onResultOf(entry -> {
                    int rating = heuristic.rate(requireNonNull(entry).request, request);
                    if (rating > thresholdExclusive) {
                        topRating.set(rating);
                    }
                    return rating;
                }))
                .map(entry -> entry.responseCreator)
                .orElse(null);
        if (topEntry != null && topRating.get() > thresholdExclusive) {
            try {
                return topEntry.createRespondable(request);
            } catch (IOException e) {
                log.warn("could not create response for top-rated entry", e);
            }
        }
        return null;
    }


    /**
     * Class that represents a HAR entry with a saved request and a method to produce
     * a response.
     */
    static class ParsedEntry {

        public final ParsedRequest request;

        public final HttpRespondableCreator responseCreator;

        public ParsedEntry(ParsedRequest request, HttpRespondableCreator responseCreator) {
            this.responseCreator = requireNonNull(responseCreator);
            this.request = requireNonNull(request);
        }

    }
}
