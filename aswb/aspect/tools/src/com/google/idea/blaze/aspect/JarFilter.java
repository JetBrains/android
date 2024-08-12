/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.aspect;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;

/** Filters a jar, keeping only the classes that are indicated. */
public final class JarFilter {

  /** The options for a {@link JarFilter} action. */
  @VisibleForTesting
  static final class JarFilterOptions {
    List<Path> filterJars;
    List<Path> filterSourceJars;
    List<Path> keepJavaFiles;
    List<Path> keepSourceJars;
    Path filteredJar;
    Path filteredSourceJar;
  }

  @VisibleForTesting
  static JarFilterOptions parseArgs(String[] args) {
    args = parseParamFileIfUsed(args);
    JarFilterOptions options = new JarFilterOptions();
    options.filterJars = OptionParser.parseMultiOption(args, "filter_jar", PATH_PARSER);
    options.filterSourceJars =
        OptionParser.parseMultiOption(args, "filter_source_jar", PATH_PARSER);
    options.keepJavaFiles = OptionParser.parseMultiOption(args, "keep_java_file", PATH_PARSER);
    options.keepSourceJars = OptionParser.parseMultiOption(args, "keep_source_jar", PATH_PARSER);
    options.filteredJar = OptionParser.parseSingleOption(args, "filtered_jar", PATH_PARSER);
    options.filteredSourceJar =
        OptionParser.parseSingleOption(args, "filtered_source_jar", PATH_PARSER);
    return options;
  }

  private static final Function<String, Path> PATH_PARSER =
      string -> FileSystems.getDefault().getPath(string);

  private static final Logger logger = Logger.getLogger(JarFilter.class.getName());

  private static final Pattern JAVA_PACKAGE_PATTERN =
      Pattern.compile("^\\s*package\\s+([\\w\\.]+);");

  public static void main(String[] args) throws Exception {
    JarFilterOptions options = parseArgs(args);
    try {
      main(options);
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "Error filtering jars", e);
      System.exit(1);
    }
    System.exit(0);
  }

  @VisibleForTesting
  static void main(JarFilterOptions options) throws Exception {
    Preconditions.checkNotNull(options.filteredJar);

    if (options.filterJars == null) {
      options.filterJars = ImmutableList.of();
    }
    if (options.filterSourceJars == null) {
      options.filterSourceJars = ImmutableList.of();
    }

    final List<String> archiveFileNamePrefixes = Lists.newArrayList();
    if (options.keepJavaFiles != null) {
      archiveFileNamePrefixes.addAll(parseJavaFiles(options.keepJavaFiles));
    }
    if (options.keepSourceJars != null) {
      archiveFileNamePrefixes.addAll(parseSrcJars(options.keepSourceJars));
    }

    filterJars(
        options.filterJars,
        options.filteredJar,
        string -> shouldKeepClass(archiveFileNamePrefixes, string));
    if (options.filteredSourceJar != null) {
      filterJars(
          options.filterSourceJars,
          options.filteredSourceJar,
          string -> shouldKeepJavaFile(archiveFileNamePrefixes, string));
    }
  }

  private static String[] parseParamFileIfUsed(String[] args) {
    if (args.length != 1 || !args[0].startsWith("@")) {
      return args;
    }
    File paramFile = new File(args[0].substring(1));
    try {
      return Files.readLines(paramFile, StandardCharsets.UTF_8).toArray(new String[0]);
    } catch (IOException e) {
      throw new RuntimeException("Error parsing param file: " + args[0], e);
    }
  }

  /** Finds the expected jar archive file name prefixes for the java files. */
  private static List<String> parseJavaFiles(List<Path> javaFiles) throws IOException {
    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

    List<ListenableFuture<String>> futures = Lists.newArrayList();
    for (final Path javaFile : javaFiles) {
      futures.add(
          executorService.submit(
              () -> {
                String packageString = getDeclaredPackageOfJavaFile(javaFile);
                return packageString != null
                    ? getArchiveFileNamePrefix(javaFile.toString(), packageString)
                    : null;
              }));
    }
    try {
      List<String> archiveFileNamePrefixes = Futures.allAsList(futures).get();
      List<String> result = Lists.newArrayList();
      for (String archiveFileNamePrefix : archiveFileNamePrefixes) {
        if (archiveFileNamePrefix != null) {
          result.add(archiveFileNamePrefix);
        }
      }
      return result;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException(e);
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }

  private static List<String> parseSrcJars(List<Path> srcJars) throws IOException {
    List<String> result = Lists.newArrayList();
    for (Path srcJar : srcJars) {
      try (ZipFile sourceZipFile = new ZipFile(srcJar.toFile())) {
        Enumeration<? extends ZipEntry> entries = sourceZipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          if (!entry.getName().endsWith(".java")) {
            continue;
          }
          try (BufferedReader reader =
              new BufferedReader(
                  new InputStreamReader(sourceZipFile.getInputStream(entry), UTF_8))) {
            String packageString = parseDeclaredPackage(reader);
            if (packageString != null) {
              String archiveFileNamePrefix =
                  getArchiveFileNamePrefix(entry.getName(), packageString);
              result.add(archiveFileNamePrefix);
            }
          }
        }
      }
    }
    return result;
  }

  @Nullable
  private static String getDeclaredPackageOfJavaFile(Path javaFile) {
    try (BufferedReader reader =
        java.nio.file.Files.newBufferedReader(javaFile, StandardCharsets.UTF_8)) {
      return parseDeclaredPackage(reader);

    } catch (IOException e) {
      logger.log(Level.WARNING, "Error parsing package string from java source: " + javaFile, e);
      return null;
    }
  }

  @Nullable
  private static String parseDeclaredPackage(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      Matcher packageMatch = JAVA_PACKAGE_PATTERN.matcher(line);
      if (packageMatch.find()) {
        return packageMatch.group(1);
      }
    }
    return null;
  }

  /**
   * Computes the expected archive file name prefix of a java class.
   *
   * <p>Eg.: file java/com/google/foo/Foo.java, package com.google.foo -> com/google/foo/Foo
   */
  private static String getArchiveFileNamePrefix(String javaFile, String packageString) {
    int lastSlashIndex = javaFile.lastIndexOf('/');
    // On Windows, the separator could be '\\'
    if (lastSlashIndex == -1) {
      lastSlashIndex = javaFile.lastIndexOf('\\');
    }
    String fileName = lastSlashIndex != -1 ? javaFile.substring(lastSlashIndex + 1) : javaFile;
    String className = fileName.substring(0, fileName.length() - ".java".length());
    return packageString.replace('.', '/') + '/' + className;
  }

  /** Filters a list of jars, keeping anything matching the passed predicate. */
  private static void filterJars(List<Path> jars, Path output, Predicate<String> shouldKeep)
      throws IOException {
    final int bufferSize = 8 * 1024;
    byte[] buffer = new byte[bufferSize];
    Set<String> names = new HashSet<>();

    try (ZipOutputStream outputStream =
        new ZipOutputStream(new FileOutputStream(output.toFile()))) {
      for (Path jar : jars) {
        try (ZipFile sourceZipFile = new ZipFile(jar.toFile())) {
          Enumeration<? extends ZipEntry> entries = sourceZipFile.entries();
          while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (!shouldKeep.test(entry.getName())) {
              continue;
            }
            if (!names.add(entry.getName())) {
              // ignore duplicate entries, on the assumption that their contents are identical
              continue;
            }

            ZipEntry newEntry = new ZipEntry(entry.getName());
            // reset creation time of entry to make it deterministic
            newEntry.setTime(0);
            newEntry.setCreationTime(FileTime.fromMillis(0));
            outputStream.putNextEntry(newEntry);
            try (InputStream inputStream = sourceZipFile.getInputStream(entry)) {
              int len;
              while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
              }
            }
          }
        }
      }
    }
  }

  @VisibleForTesting
  static boolean shouldKeepClass(List<String> archiveFileNamePrefixes, String name) {
    if (!name.endsWith(".class")) {
      return false;
    }
    for (String archiveFileNamePrefix : archiveFileNamePrefixes) {
      if (name.startsWith(archiveFileNamePrefix)
          && name.length() > archiveFileNamePrefix.length()) {
        char c = name.charAt(archiveFileNamePrefix.length());
        if (c == '.' || c == '$') {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean shouldKeepJavaFile(List<String> archiveFileNamePrefixes, String name) {
    if (!name.endsWith(".java")) {
      return false;
    }
    String nameWithoutJava = name.substring(0, name.length() - ".java".length());
    return archiveFileNamePrefixes.contains(nameWithoutJava);
  }
}
