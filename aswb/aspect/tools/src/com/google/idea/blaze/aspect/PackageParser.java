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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.devtools.intellij.aspect.Common.ArtifactLocation;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.JavaSourcePackage;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PackageManifest;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Parses the package string from each of the source .java files. */
public class PackageParser {

  /** The options for a {@link PackageParser} action. */
  @VisibleForTesting
  static final class PackageParserOptions {
    List<ArtifactLocation> sources;
    Path outputManifest;
  }

  @VisibleForTesting
  static PackageParserOptions parseArgs(String[] args) {
    args = parseParamFileIfUsed(args);
    PackageParserOptions options = new PackageParserOptions();
    options.sources =
        OptionParser.parseSingleOption(args, "sources", ArtifactLocationParser::parseList);
    options.outputManifest =
        OptionParser.parseSingleOption(
            args, "output_manifest", string -> FileSystems.getDefault().getPath(string));
    return options;
  }

  private static final Logger logger = Logger.getLogger(PackageParser.class.getName());

  private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w\\.]+)");

  public static void main(String[] args) throws Exception {
    PackageParserOptions options = parseArgs(args);
    Preconditions.checkNotNull(options.outputManifest);

    try {
      PackageParser parser = new PackageParser(PackageParserIoProvider.INSTANCE);
      Map<ArtifactLocation, String> outputMap = parser.parsePackageStrings(options.sources);
      parser.writeManifest(outputMap, options.outputManifest);
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "Error parsing package strings", e);
      System.exit(1);
    }
    System.exit(0);
  }

  private static Path getExecutionPath(ArtifactLocation location) {
    return Paths.get(location.getRootExecutionPathFragment(), location.getRelativePath());
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

  private final PackageParserIoProvider ioProvider;

  @VisibleForTesting
  PackageParser(PackageParserIoProvider ioProvider) {
    this.ioProvider = ioProvider;
  }

  @VisibleForTesting
  void writeManifest(Map<ArtifactLocation, String> sourceToPackageMap, Path outputFile)
      throws IOException {
    PackageManifest.Builder builder = PackageManifest.newBuilder();
    for (Entry<ArtifactLocation, String> entry : sourceToPackageMap.entrySet()) {
      JavaSourcePackage.Builder srcBuilder =
          JavaSourcePackage.newBuilder()
              .setPackageString(entry.getValue())
              .setArtifactLocation(entry.getKey());
      builder.addSources(srcBuilder.build());
    }

    try {
      ioProvider.writeProto(builder.build(), outputFile);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error writing package manifest", e);
      throw e;
    }
  }

  @VisibleForTesting
  Map<ArtifactLocation, String> parsePackageStrings(List<ArtifactLocation> sources)
      throws Exception {

    ListeningExecutorService executorService =
        MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

    Map<ArtifactLocation, ListenableFuture<String>> futures = Maps.newHashMap();
    for (final ArtifactLocation source : sources) {
      futures.put(source, executorService.submit(() -> getDeclaredPackageOfJavaFile(source)));
    }
    Map<ArtifactLocation, String> map = Maps.newHashMap();
    for (Entry<ArtifactLocation, ListenableFuture<String>> entry : futures.entrySet()) {
      String value = entry.getValue().get();
      if (value != null) {
        map.put(entry.getKey(), value);
      }
    }
    return map;
  }

  @Nullable
  private String getDeclaredPackageOfJavaFile(ArtifactLocation source) {
    try (BufferedReader reader = ioProvider.getReader(getExecutionPath(source))) {
      return parseDeclaredPackage(reader);

    } catch (IOException e) {
      logger.log(Level.WARNING, "Error parsing package string from java source: " + source, e);
      return null;
    }
  }

  @Nullable
  private static String parseDeclaredPackage(BufferedReader reader) throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      Matcher packageMatch = PACKAGE_PATTERN.matcher(line);
      if (packageMatch.find()) {
        return packageMatch.group(1);
      }
    }
    return null;
  }
}
