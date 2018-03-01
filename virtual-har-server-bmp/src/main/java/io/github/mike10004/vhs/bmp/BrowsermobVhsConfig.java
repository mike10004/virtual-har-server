package io.github.mike10004.vhs.bmp;

import org.apache.commons.io.FileUtils;

import java.nio.file.Path;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    public Path scratchDir;
    private final ResponseManufacturer manufacturer;

    private BrowsermobVhsConfig(Builder builder) {
        scratchDir = builder.scratchDir;
        this.manufacturer = builder.manufacturer;
    }

    public static Builder builder(ResponseManufacturer manufacturer) {
        return new Builder(manufacturer);
    }

    public static final class Builder {

        private Path scratchDir = FileUtils.getTempDirectory().toPath();
        private final ResponseManufacturer manufacturer;

        private Builder(ResponseManufacturer manufacturer) {
            this.manufacturer = requireNonNull(manufacturer);
        }

        public Builder scratchDir(Path val) {
            scratchDir = requireNonNull(val);
            return this;
        }

        public BrowsermobVhsConfig build() {
            return new BrowsermobVhsConfig(this);
        }
    }

    public ResponseManufacturer createResponseManufacturer() {
        return manufacturer;
    }
}
