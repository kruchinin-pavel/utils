package org.kpa.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public class FileRef {
    private Path path;
    private Path secondPath;
    private Multimap<String, Long> lineNo = TreeMultimap.create();

    public FileRef() {
    }

    public FileRef(Path path) {
        this.path = path;
    }

    public FileRef(FileRef fileRef, String reason) {
        this(fileRef.path);
        fileRef.lineNo.values().forEach(v -> lineNo.put(reason, v));
    }

    public FileRef(Path path, long lineNo) {
        this(path);
        this.lineNo.put("", lineNo);
    }

    public void setSecondPath(Path secondPath) {
        this.secondPath = secondPath;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public Path getSecondPath() {
        return secondPath;
    }

    public Collection<Map.Entry<String, Long>> getLineNo() {
        return lineNo.entries();
    }

    public void setLineNo(Collection<Map.Entry<String, Long>> lineNo) {
        lineNo.forEach(e -> {
            this.lineNo.put(e.getKey(), e.getValue());
        });
    }

    @JsonIgnore
    public FileRef reason(String reason) {
        return new FileRef(this, reason);
    }

    public Path getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "FileRef{" +
                "path=" + path +
                (secondPath != null ? ", secondPath=" + path : "") +
                ", lineNo=" + lineNo +
                '}';
    }

    @JsonIgnore
    public FileRef add(FileRef fileRef) {
        if (lineNo.size() > 100) {
            return this;
        }
        FileRef joined = new FileRef(path);
        if (!path.equals(fileRef.path)) {
            joined.secondPath = fileRef.path;
        }
        joined.lineNo.putAll(lineNo);
        joined.lineNo.putAll(fileRef.lineNo);
        return joined;
    }
}
