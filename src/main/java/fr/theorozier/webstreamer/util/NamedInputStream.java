package fr.theorozier.webstreamer.util;

import java.io.FilterInputStream;
import java.io.InputStream;

public class NamedInputStream extends FilterInputStream {
    private final String name;

    public NamedInputStream(InputStream in, String name) {
        super(in);
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
