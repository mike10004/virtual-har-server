package io.github.mike10004.vhs.bmp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.HarEntry;
import io.github.mike10004.vhs.BasicHeuristic;
import io.github.mike10004.vhs.EntryMatcher;
import io.github.mike10004.vhs.EntryMatcherFactory;
import io.github.mike10004.vhs.EntryParser;
import io.github.mike10004.vhs.HarBridgeEntryParser;
import io.github.mike10004.vhs.HeuristicEntryMatcher;
import io.github.mike10004.vhs.ResponseInterceptor;
import io.github.mike10004.vhs.harbridge.sstoehr.SstoehrHarBridge;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static java.util.Objects.requireNonNull;

public class BmpTests {

    private BmpTests() {}

    private static final KeystoreDataCache keystoreDataCache =
            new KeystoreDataCache(new KeystoreGenerator(KeystoreGenerator.KeystoreType.PKCS12,
                    new MemorySecurityProviderTool(),
                    new Random(KeystoreDataCache.class.getName().hashCode())));

    private static class KeystoreDataCache {
        private final KeystoreDataSerializer keystoreDataSerializer;
        private final LoadingCache<Optional<String>, String> serializedFormCache;

        public KeystoreDataCache(KeystoreGenerator keystoreGenerator) {
            requireNonNull(keystoreGenerator);
            keystoreDataSerializer = KeystoreDataSerializer.getDefault();
            //noinspection NullableProblems
            serializedFormCache = CacheBuilder.newBuilder()
                    .build(new CacheLoader<Optional<String>, String>() {
                        @Override
                        public String load(Optional<String> key) throws Exception {
                            KeystoreData keystoreData = keystoreGenerator.generate(key.orElse(null));
                            return keystoreDataSerializer.serialize(keystoreData);
                        }
                    });
        }

        public KeystoreData get(@Nullable String certificateCommonName) {
            String serializedForm = serializedFormCache.getUnchecked(Optional.ofNullable(certificateCommonName));
            return keystoreDataSerializer.deserialize(serializedForm);
        }
    }

    public static KeystoreData generateKeystoreForUnitTest(@Nullable String certificateCommonName) {
        return keystoreDataCache.get(certificateCommonName);
    }

    public static HarReplayManufacturer createManufacturer(File harFile, Iterable<ResponseInterceptor> responseInterceptors, BmpResponseListener responseListener) throws IOException {
        List<HarEntry> entries;
        try {
            entries = new de.sstoehr.harreader.HarReader().readFromFile(harFile).getLog().getEntries();
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        System.out.println("requests contained in HAR:");
        entries.stream().map(HarEntry::getRequest).forEach(request -> {
            System.out.format("  %s %s%n", request.getMethod(), request.getUrl());
        });
        EntryMatcherFactory entryMatcherFactory = HeuristicEntryMatcher.factory(new BasicHeuristic(), BasicHeuristic.DEFAULT_THRESHOLD_EXCLUSIVE);
        EntryParser<HarEntry> parser = new HarBridgeEntryParser<>(new SstoehrHarBridge());
        EntryMatcher entryMatcher = entryMatcherFactory.createEntryMatcher(entries, parser);
        return new HarReplayManufacturer(entryMatcher, responseInterceptors, responseListener);
    }

}
