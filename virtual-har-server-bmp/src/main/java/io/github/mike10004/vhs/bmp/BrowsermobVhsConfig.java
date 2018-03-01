package io.github.mike10004.vhs.bmp;

import org.apache.commons.io.FileUtils;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class BrowsermobVhsConfig {

    public Path scratchDir;

    private BrowsermobVhsConfig(Builder builder) {
        scratchDir = builder.scratchDir;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Path scratchDir = FileUtils.getTempDirectory().toPath();

        private Builder() {
        }

        public Builder scratchDir(Path val) {
            scratchDir = requireNonNull(val);
            return this;
        }

        public BrowsermobVhsConfig build() {
            return new BrowsermobVhsConfig(this);
        }
    }
}
