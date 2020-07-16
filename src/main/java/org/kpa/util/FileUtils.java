package org.kpa.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class FileUtils {
    private static final Map<String, FileSystem> fileSystemMap = new ConcurrentHashMap<>();

    public static BufferedReader newBufferedReader(String fileName) {
        return newBufferedReader(path(fileName));
    }

    public static BufferedReader newBufferedReader(Path path) {
        try {
            InputStream is = getInputStream(path.toString());
            return new BufferedReader(new InputStreamReader(new BOMInputStream(is)));
        } catch (IOException e) {
            throw new RuntimeException("Exception with [" + path + "]: " + e.getMessage(), e);
        }
    }

    public static InputStream getInputStream(String path) throws IOException {
        InputStream is;
        if (path.contains("!") &&
                Splitter.on("!").trimResults().omitEmptyStrings().split(path).iterator().next().endsWith(".tar.gz")) {
            FileSystemManager fsManager = VFS.getManager();
            FileObject tz;
            try {
                tz = fsManager.resolveFile(path);
            } catch (org.apache.commons.vfs2.FileSystemException e) {
                tz = fsManager.resolveFile(Paths.get(path).toAbsolutePath().toString());
            }
            return tz.getContent().getInputStream();
        } else if (path.endsWith("gz")) {
            is = new GZIPInputStream(Files.newInputStream(Paths.get(path)));
        } else {
            is = Files.newInputStream(Paths.get(path));
        }
        return is;
    }

    public static BufferedWriter newBufferedWriter(String path) {
        return newBufferedWriter(path(path));
    }

    public static BufferedWriter newBufferedWriter(Path path) {
        try {
            if (path.toString().endsWith("gz")) {
                GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(path), 512, true);
                return new BufferedWriter(new OutputStreamWriter(out){
                    @Override
                    public void close() throws IOException {
                        super.close();
                        out.close();
                    }
                });
            }
            return Files.newBufferedWriter(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path path(String fileName) {
        if (fileName.endsWith("zip")) {
            return FileUtils.openFirst(fileName);
        }
        return Paths.get(fileName);
    }

    public static Path openFirst(String fileName) {
        try {
            return stream(fileName).findFirst().orElseGet(null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Stream<Path> stream(String fileName) throws IOException {
        return StreamSupport.stream(Files.newDirectoryStream(get(fileName).getPath("/")).spliterator(), false);
    }

    private static FileSystem get(String fileName) throws IOException {
        synchronized (fileSystemMap) {
            if (fileSystemMap.containsKey(fileName)) {
                return fileSystemMap.get(fileName);
            }
            Path zipfile = Paths.get(fileName);
            FileSystem fileSystem = FileSystems.newFileSystem(zipfile, ClassLoader.getSystemClassLoader());
            fileSystemMap.put(fileName, fileSystem);
            return fileSystem;
        }
    }

    public static Iterable<String> lines(String fileName) {
        return () -> {
            BufferedReader reader = newBufferedReader(fileName);

            return new Iterator<String>() {
                String line = null;

                @Override
                public boolean hasNext() {
                    if (line == null) {
                        try {
                            line = reader.readLine();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    if (line == null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return line != null;
                }

                @Override
                public String next() {
                    hasNext();
                    String line = this.line;
                    this.line = null;
                    return line;
                }

                @Override
                protected void finalize() throws Throwable {
                    if (reader != null) reader.close();
                }
            };
        };
    }

    public static List<String> list(String... args) {
        return list(Arrays.asList(args));
    }

    public static List<String> list(Iterable<String> args) {
        List<String> params = new ArrayList<>();
        args.forEach(arg -> {
            try {
                if (arg.endsWith(".tar.gz")) {
                    Path path = Paths.get(arg).toAbsolutePath();
                    Preconditions.checkArgument(Files.isRegularFile(path), "Not exits: %s", path);
                    FileSystemManager fsManager = VFS.getManager();
                    FileObject tz = fsManager.resolveFile("tgz://" + path);
                    for (FileObject fo : tz.getChildren()) {
                        params.add(fo.toString());
                    }
                } else {
                    Path p = Paths.get(arg);
                    if (Files.isDirectory(p)) {
                        params.addAll(Files.find(p, 100, (path, basicFileAttributes) -> Files.isRegularFile(path)).map(Path::toString).collect(Collectors.toList()));
                    } else if (Files.isRegularFile(p)) {
                        params.add(p.toString());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Preconditions.checkArgument(params.size() > 0, "Nothing found in directories: %s", Arrays.asList(args));
        return params;
    }

    private static final DateTimeFormatter ldtBackupFileF = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS");

    public static String addTs(String fileName) {
        String suffix = "";
        String name;
        int i = 0;
        while ((name = implAddTs(fileName, suffix)) == null) {
            suffix = "." + (i++);
        }
        return name;
    }

    private static String implAddTs(String fileName, String suffix) {

        File file = new File(fileName);
        String ext = FilenameUtils.getExtension(fileName);
        String name = FilenameUtils.getBaseName(fileName);
        File resFile = new File(FilenameUtils.concat(
                file.getParent(),
                String.format("%s.%s%s.%s", name, ldtBackupFileF.format(LocalDateTime.now()), suffix, ext)));
        return resFile.exists() ? null : resFile.toString();
    }
}
