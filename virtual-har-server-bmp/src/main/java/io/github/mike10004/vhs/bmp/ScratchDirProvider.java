package io.github.mike10004.vhs.bmp;

import org.apache.commons.io.FileUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

public interface ScratchDirProvider {

    interface Scratch extends java.io.Closeable {

        Path getRoot();

        default void close() throws IOException {
            try {
                FileUtils.forceDelete(getRoot().toFile());
            } catch (FileNotFoundException ignore) {
            }
        }
    }

    Scratch createScratchDir() throws IOException;

    static ScratchDirProvider under(Path parent) {
        return under(parent, "virtual-har-server");
    }

    static ScratchDirProvider under(Path parent, String prefix) {
        return new TempScratchDirProvider(parent, prefix);
    }

}
