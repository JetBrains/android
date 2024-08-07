/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Utility for finding inner paths of a source jar corresponding to package roots */
public class SrcJarInnerPathFinder {

  /** Indicates whether or not non-empty package prefixes are allowed. */
  public enum AllowPackagePrefixes {
    /**
     * Empty package prefixes only. Files where the java package does not match the srcjar path will
     * be ignored.
     */
    EMPTY_PACKAGE_PREFIXES_ONLY,
    /** Non empty package prefixes are allowed. */
    ALLOW_NON_EMPTY_PACKAGE_PREFIXES;
  }

  /** Represents a path within a srcjar file, and a package prefix to use for that path. */
  public record JarPath(Path path, String packagePrefix) {
    static JarPath create(String path, String packagePrefix) {
      return new JarPath(Path.of(path), packagePrefix);
    }

    static JarPath create(Path path, String packagePrefix) {
      return new JarPath(path, packagePrefix);
    }
  }

  private final Logger logger = Logger.getLogger(SrcJarInnerPathFinder.class.getSimpleName());
  private final PackageStatementParser packageStatementParser;

  public SrcJarInnerPathFinder(PackageStatementParser packageStatementParser) {
    this.packageStatementParser = packageStatementParser;
  }

  public ImmutableSet<JarPath> findInnerJarPaths(
      File jarFile, AllowPackagePrefixes allowPackagePrefixes, String fileNameForLogs) {
    try (ZipFile zip = new ZipFile(jarFile)) {
      return findInnerJarPaths(zip, allowPackagePrefixes, fileNameForLogs);
    } catch (IOException ioe) {
      logger.log(Level.WARNING, "Failed to examine " + jarFile + ": " + ioe);
      // return the jar file root to ensure we don't ignore it.
      return ImmutableSet.of(JarPath.create("", ""));
    }
  }

  public ImmutableSet<JarPath> findInnerJarPaths(
      CachedArtifact artifact, AllowPackagePrefixes allowPackagePrefixes, String fileNameForLogs) {
    try (ZipFile zip = artifact.openAsZipFile()) {
      return findInnerJarPaths(zip, allowPackagePrefixes, fileNameForLogs);
    } catch (IOException ioe) {
      logger.log(
          Level.WARNING,
          "Failed to examine " + fileNameForLogs + "; artifact: " + artifact + ": " + ioe);
      // return the jar file root to ensure we don't ignore it.
      return ImmutableSet.of(JarPath.create("", ""));
    }
  }

  private ImmutableSet<JarPath> findInnerJarPaths(
      ZipFile zip, AllowPackagePrefixes allowPackagePrefixes, String fileNameForLogs)
      throws IOException {
    Set<JarPath> paths = Sets.newHashSet();
    Set<Path> topLevelPaths = Sets.newHashSet();
    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry e = entries.nextElement();
      if (e.isDirectory()) {
        continue;
      }
      Path zipfilePath = Path.of(e.getName());
      if (!(zipfilePath.getFileName().toString().endsWith(".java")
          || zipfilePath.getFileName().toString().endsWith(".kt"))) {
        continue;
      }
      if (!topLevelPaths.add(zipfilePath.getName(0))) {
        continue;
      }
      String pname;
      try (InputStream in = zip.getInputStream(e)) {
        pname = packageStatementParser.readPackage(in);
      }
      Path packageAsPath = Path.of(pname.replace('.', '/'));
      Path zipPath = zipfilePath.getParent();
      if (zipPath == null) {
        zipPath = Path.of("");
      }
      if (zipPath.equals(packageAsPath)) {
        // package root is the jar file root.
        paths.add(JarPath.create("", ""));
      } else if (zipPath.endsWith(packageAsPath)) {
        paths.add(
            JarPath.create(
                zipPath.subpath(0, zipPath.getNameCount() - packageAsPath.getNameCount()), ""));
      } else {
        if (allowPackagePrefixes == AllowPackagePrefixes.ALLOW_NON_EMPTY_PACKAGE_PREFIXES) {
          paths.add(JarPath.create(zipPath, pname));
        } else {
          logger.log(
              Level.WARNING,
              "Java package name "
                  + pname
                  + " does not match srcjar path "
                  + zipfilePath
                  + " in "
                  + fileNameForLogs);
        }
      }
    }
    if (paths.isEmpty()) {
      // we didn't find any java/kt sources. Add the jar file root to ensure we don't ignore it.
      paths.add(JarPath.create("", ""));
    }
    return ImmutableSet.copyOf(paths);
  }
}
