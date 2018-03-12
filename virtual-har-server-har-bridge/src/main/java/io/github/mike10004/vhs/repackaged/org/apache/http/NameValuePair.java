package io.github.mike10004.vhs.repackaged.org.apache.http;

public interface NameValuePair {

    String getName();
    String getValue();

    static NameValuePair of(String name, String value) {
        return new NameValuePair() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getValue() {
                return value;
            }
        };
    }
}
