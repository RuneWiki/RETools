package org.runewiki.deob;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// walk the directory and read all java files inside
// for each java file, read the class name and store it in the mappings
public class GenerateRemap {
    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile("package\\s+(.*);");
    private static final Pattern ORIGINAL_CLASS_PATTERN = Pattern.compile("@OriginalClass\\(\"(\\w+?)!(\\w+)\"\\)");
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("class\\s+(\\w+)");

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: GenerateRemap <source dir>");
            System.exit(1);
        }

        String dir = args[0];
        Map<String, String> mappings = new HashMap<>();

        try {
            Path path = Paths.get("remap.txt");
            if (Files.exists(path)) {
                Files.lines(path).forEach(line -> {
                    String[] parts = line.split("=");
                    mappings.put(parts[0], parts[1]);
                });
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        // todo: prefix inner classes with outer class name
        try {
            Files.walk(Paths.get(dir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(p -> {
                    try {
                        String pkg = null;
                        String obfName = null;

                        List<String> lines = Files.readAllLines(p);
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);

                            if (obfName != null) {
                                Matcher classNameMatcher = CLASS_NAME_PATTERN.matcher(line);
                                if (classNameMatcher.find()) {
                                    String className = classNameMatcher.group(1);
                                    if (pkg != null) {
                                        mappings.put(obfName, pkg + "/" + className);
                                    } else {
                                        mappings.put(obfName, className);
                                    }
                                    obfName = null;
                                }
                                continue;
                            }

                            Matcher packageNameMatcher = PACKAGE_NAME_PATTERN.matcher(line);
                            Matcher originalClassMatcher = ORIGINAL_CLASS_PATTERN.matcher(line);

                            if (packageNameMatcher.find()) {
                                pkg = packageNameMatcher.group(1).replace(".", "/");
                            } else if (originalClassMatcher.find()) {
                                String library = originalClassMatcher.group(1);
                                obfName = originalClassMatcher.group(2);

                                if (!library.equals("client")) {
                                    return;
                                }

                                Matcher classNameMatcher = CLASS_NAME_PATTERN.matcher(lines.get(i));
                                if (classNameMatcher.find()) {
                                    String className = classNameMatcher.group(1);
                                    if (pkg != null) {
                                        mappings.put(obfName, pkg + "/" + className);
                                    } else {
                                        mappings.put(obfName, className);
                                    }
                                    obfName = null;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            Path path = Paths.get("remap.txt");
            BufferedWriter writer = Files.newBufferedWriter(path);

            List<String> keys = new ArrayList<>(mappings.keySet());
            keys.sort(Comparator.naturalOrder());
            for (String key : keys) {
                writer.write(key + "=" + mappings.get(key));
                writer.newLine();
            }

            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
