/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zip comma-separate list of resource files and a manifest file into as an AAR file */
public class CreateAar {
  private static final Logger logger = Logger.getLogger(JarFilter.class.getName());

  private static final String AAR_MANIFEST_NAME = "AndroidManifest.xml";
  private static final String AAR_RESOURCE_DIR = "res";

  private static File fileParser(String string) {
    return new File(string);
  }

  private static Set<File> filelistParser(String string) {
    return Arrays.stream(string.split(","))
        .map(filename -> new File(filename))
        .collect(Collectors.toSet());
  }

  @VisibleForTesting
  static final class AarOptions {
    File outputAar;
    File manifestFile;
    Set<File> resourceFiles;
    String resourceRoot;
  }

  @VisibleForTesting
  static AarOptions parseArgs(String[] args) {
    args = parseParamFileIfUsed(args);
    AarOptions options = new AarOptions();
    options.outputAar = OptionParser.parseSingleOption(args, "aar", CreateAar::fileParser);
    options.manifestFile =
        OptionParser.parseSingleOption(args, "manifest_file", CreateAar::fileParser);
    options.resourceFiles =
        OptionParser.parseSingleOption(args, "resources", CreateAar::filelistParser);
    options.resourceRoot = OptionParser.parseSingleOption(args, "resource_root", String::toString);
    return options;
  }

  @VisibleForTesting
  public static void main(String[] args) {
    AarOptions options = parseArgs(args);
    try {
      main(options);
    } catch (Throwable e) {
      logger.log(Level.SEVERE, "Error filtering jars", e);
      System.exit(1);
    }
    System.exit(0);
  }

  public static void main(AarOptions options) {
    if (!options.resourceFiles.isEmpty()) {
      Preconditions.checkNotNull(options.outputAar);
      Preconditions.checkNotNull(options.manifestFile);
      Preconditions.checkNotNull(options.resourceRoot);
      if (options.outputAar.exists()) {
        options.outputAar.delete();
      }

      try (FileOutputStream fos = new FileOutputStream(options.outputAar.getPath());
          ZipOutputStream zos = new ZipOutputStream(fos)) {
        for (File resourceFile : options.resourceFiles) {
          int startIndex = options.resourceRoot.length();
          addFileToAar(
              resourceFile, AAR_RESOURCE_DIR + resourceFile.getPath().substring(startIndex), zos);
        }
        addFileToAar(options.manifestFile, AAR_MANIFEST_NAME, zos);
      } catch (FileNotFoundException e) {
        logger.log(Level.SEVERE, "Fail to generate aar file", e);
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Fail to zip aar file", e);
      }
    }
  }

  public static void addFileToAar(File file, String dest, ZipOutputStream zos) throws IOException {
    try (FileInputStream fis = new FileInputStream(file)) {
      ZipEntry zipEntry = new ZipEntry(dest);
      // reset creation time of entry to make it deterministic
      zipEntry.setTime(0);
      zipEntry.setCreationTime(FileTime.fromMillis(0));
      zos.putNextEntry(zipEntry);
      ByteStreams.copy(fis, zos);
      zos.closeEntry();
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
      throw new IllegalStateException("Error parsing param file: " + args[0], e);
    }
  }

  private CreateAar() {}
}
