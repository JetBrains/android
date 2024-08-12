/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.android.libraries;

import static com.android.SdkConstants.FN_LINT_JAR;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.command.buildresult.LocalFileArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Unzip prefetched aars to local cache directories. AARs are directories with many files. {@see
 * https://developer.android.com/studio/projects/android-library.html#aar-contents}, for a subset of
 * the contents (documentation may be outdated).
 *
 * <p>The IDE wants at least the following:
 *
 * <ul>
 *   <li>the res/ folder
 *   <li>the R.txt file adjacent to the res/ folder
 *   <li>See {@link com.android.tools.idea.resources.aar.AarSourceResourceRepository} for the
 *       dependency on R.txt.
 *   <li>jars: we use the merged output jar from Bazel instead of taking jars from the AAR. It gives
 *       us freedom in the future to use an ijar or header jar instead, which is more lightweight.
 *       It should be placed in a jars/ folder adjacent to the res/ folder. See {@link
 *       org.jetbrains.android.uipreview.ModuleClassLoader}, for that possible assumption.
 *   <li>The IDE may want the AndroidManifest.xml as well.
 * </ul>
 */
public final class Unpacker {
  private static final Logger logger = Logger.getInstance(Unpacker.class);
  // Jars that are expected to be extracted from .aar to local
  private static final ImmutableSet<String> EXPECTED_JARS =
      ImmutableSet.of(FN_LINT_JAR, "inspector.jar");

  /** Updated prefetched aars to aar directory. */
  public static void unpack(
      ImmutableMap<String, AarLibraryContents> toCache, Set<String> updatedKeys, AarCache aarCache)
      throws ExecutionException, InterruptedException {
    unpackAarsToDir(toCache, updatedKeys, aarCache);
  }

  private static void unpackAarsToDir(
      ImmutableMap<String, AarLibraryContents> toCache, Set<String> updatedKeys, AarCache aarCache)
      throws ExecutionException, InterruptedException {
    FileOperationProvider ops = FileOperationProvider.getInstance();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    updatedKeys.forEach(
        key ->
            futures.add(
                FetchExecutor.EXECUTOR.submit(
                    () -> unpackAarToDir(ops, toCache.get(key), aarCache))));
    Futures.allAsList(futures).get();
  }

  /**
   * Each .aar file will be unpacked as <key_from_artifact_location>.aar directories in cache
   * directory. A timestamp file will be created to decide if updated is needed when a new .aar file
   * with same name is found next time.
   */
  private static void unpackAarToDir(
      FileOperationProvider ops, AarLibraryContents aarLibraryContents, AarCache aarCache) {
    String cacheKey = UnpackedAarUtils.getAarDirName(aarLibraryContents.aar());
    try {
      File aarDir = aarCache.recreateAarDir(ops, cacheKey);
      // TODO(brendandouglas): decompress via ZipInputStream so we don't require a local file
      File toCopy = getOrCreateLocalFile(aarLibraryContents.aar());
      ZipUtil.extract(
          toCopy,
          aarDir,
          // Skip jars except EXPECTED_JARS. We will copy jar in AarLibraryContents instead.
          // That could give us freedom in the future to use an ijar or header jar instead,
          // which is more lightweight. For EXPECTED_JARS, they are not collected JarLibrary,
          // so that we are not able to copy them from AarLibraryContents. But we need them for
          // some functions e.g. lint check, lay out inspection etc. So copy them directly.
          (dir, name) -> EXPECTED_JARS.contains(name) || !name.endsWith(".jar"));

      BlazeArtifact aar = aarLibraryContents.aar();

      try {
        aarCache.createTimeStampFile(
            cacheKey,
            (aar instanceof LocalFileArtifact) ? ((LocalFileArtifact) aar).getFile() : null);
      } catch (IOException e) {
        logger.warn("Failed to set AAR cache timestamp for " + aar, e);
      }

      // copy merged jar
      if (aarLibraryContents.jar() != null) {
        try (InputStream stream = aarLibraryContents.jar().getInputStream()) {
          Path destination = Paths.get(UnpackedAarUtils.getJarFile(aarDir).getPath());
          ops.mkdirs(destination.getParent().toFile());
          Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
          logger.warn("Failed to copy class jar for " + aar, e);
        }
      }

      // copy src jars
      for (BlazeArtifact srcjar : aarLibraryContents.srcJars()) {
        try (InputStream stream = srcjar.getInputStream()) {
          Path destination = aarDir.toPath().resolve(UnpackedAarUtils.getSrcJarName(srcjar));
          ops.mkdirs(destination.getParent().toFile());
          Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
          logger.warn("Failed to copy source jar for " + aar, e);
        }
      }
    } catch (IOException e) {
      logger.warn(
          String.format(
              "Failed to extract AAR %s to %s",
              aarLibraryContents.aar(), aarCache.aarDirForKey(cacheKey)),
          e);
    }
  }

  /** Returns a locally-accessible file mirroring the contents of this {@link BlazeArtifact}. */
  private static File getOrCreateLocalFile(BlazeArtifact artifact) throws IOException {
    if (artifact instanceof LocalFileArtifact) {
      return ((LocalFileArtifact) artifact).getFile();
    }
    File tmpFile =
        FileUtil.createTempFile(
            "local-aar-file",
            Integer.toHexString(UnpackedAarUtils.getArtifactKey(artifact).hashCode()),
            /* deleteOnExit= */ true);
    try (InputStream stream = artifact.getInputStream()) {
      Files.copy(stream, Paths.get(tmpFile.getPath()), StandardCopyOption.REPLACE_EXISTING);
      return tmpFile;
    }
  }

  private Unpacker() {}
}
