/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.resources.aar;

import com.intellij.openapi.util.io.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.NotNull;

/**
 * A command-line program for packaging framework resources into framework_res.jar. The jar file
 * created by this program contains compressed XML resource files and two binary files,
 * resources.bin and resources_light.bin. Format of these binary files is identical to format of
 * a framework resource cache file without a header. The resources.bin file contains a list of all
 * framework resources. The resources_light.bin file contains a list of resources excluding
 * locale-specific ones.
 */
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class FrameworkResJarCreator {
  public static void main(@NotNull String[] args) {
    if (args.length != 2) {
      printUsage(FrameworkResJarCreator.class.getName());
      System.exit(1);
    }

    Path resDirectory = Paths.get(args[0]).toAbsolutePath().normalize();
    Path jarFile = Paths.get(args[1]).toAbsolutePath().normalize();
    try {
      createJar(resDirectory, jarFile);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void createJar(@NotNull Path resDirectory, @NotNull Path jarFile) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarFile))) {
      createZipEntry(FrameworkResourceRepository.ENTRY_NAME_WITH_LOCALES, getEncodedResources(resDirectory, true), zip);
      createZipEntry(FrameworkResourceRepository.ENTRY_NAME_WITHOUT_LOCALES, getEncodedResources(resDirectory, false), zip);
      Path parentDir = resDirectory.getParent();
      Files.walkFileTree(resDirectory, new SimpleFileVisitor<Path>() {
        @Override
        @NotNull
        public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
          // When running on Windows, we need to make sure that the file entries are correctly encoded with the Unix path separator since the
          // ZIP file spec only allows for that one.
          String relativePath = FileUtil.toSystemIndependentName(parentDir.relativize(file).toString());
          if (!relativePath.equals("res/version")) { // Skip the "version" file.
            createZipEntry(relativePath, Files.readAllBytes(file), zip);
          }
          return FileVisitResult.CONTINUE;
        }
      });
    }
  }

  private static void createZipEntry(@NotNull String name, @NotNull byte[] content, @NotNull ZipOutputStream zip) throws IOException {
    ZipEntry entry = new ZipEntry(name);
    zip.putNextEntry(entry);
    zip.write(content);
    zip.closeEntry();
  }

  private static byte[] getEncodedResources(@NotNull Path resDirectory, boolean withLocaleResources) throws IOException {
    FrameworkResourceRepository repository = FrameworkResourceRepository.create(resDirectory, withLocaleResources, null);
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (Base128OutputStream stream = new Base128OutputStream(byteStream)) {
      repository.writeToStream(stream);
    }
    return byteStream.toByteArray();
  }

  private static void printUsage(@NotNull String programName) {
    System.out.println(String.format("Usage: %s <res_directory> <jar_file>", programName));
  }
}
