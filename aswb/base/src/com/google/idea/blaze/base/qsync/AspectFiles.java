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
package com.google.idea.blaze.base.qsync;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.common.experiments.StringExperiment;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.jgoodies.common.base.Strings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;

/** Low-level utility methods for copying aspect files. */
public final class AspectFiles {

  private static final Logger logger = Logger.getInstance(AspectFiles.class);

  public static final StringExperiment aspectLocation =
      new StringExperiment("qsync.build.aspect.location");

  private static final String BUNDLED_ASPECTS_DIR = "aspect";

  private final WorkspaceRoot workspaceRoot;

  public AspectFiles(WorkspaceRoot workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  /**
   * Gets a bundled aspect file as a {@link Path}.
   *
   * @param filename The simple filename of the aspect to load from the bundled resources.
   */
  public Path getBundledAspectPath(String filename) {
    String aspectPath = System.getProperty(String.format("qsync.aspect.%s.file", filename));
    if (aspectPath != null) {
      return Path.of(aspectPath);
    }
    if (Strings.isNotEmpty(aspectLocation.getValue())) {
      Path workspaceAbsolutePath = workspaceRoot.absolutePathFor("");
      // NOTE: aspectLocation allows both relative and absolute paths.
      ImmutableList<Path> candidates =
          Splitter.on(":")
              .splitToStream(aspectLocation.getValue())
              .map(workspaceAbsolutePath::resolve)
              .map(it -> it.resolve(BUNDLED_ASPECTS_DIR).resolve(filename))
              .collect(toImmutableList());

      final var result =
          candidates.stream()
              .filter(Files::exists)
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(Locale.ROOT, "None of %s exists", candidates)));
      logger.info("Using build aspect file: " + result);
      return result;
    } else {
      PluginDescriptor plugin = checkNotNull(PluginManager.getPluginByClass(AspectFiles.class));
      return plugin.getPluginPath().resolve(BUNDLED_ASPECTS_DIR).resolve(filename);
    }
  }

  /**
   * Copies the given content to the destination path.
   *
   * @return The path to the file that was written.
   */
  public void copyInvocationFile(Path workspaceRelativeTargetPath, ByteSource content)
      throws BuildException {
    IOException lastException = null;
    for (var attempt = 0; attempt < 3; attempt++) {
      lastException = null;
      Path absolutePath = workspaceRoot.path().resolve(workspaceRelativeTargetPath);
      try {
        Files.createDirectories(absolutePath.getParent());
      } catch (IOException ex) {
        logger.warn("Failed to create directory " + absolutePath.getParent(), ex);
        continue;
      }
      try {
        if (Files.exists(absolutePath)
            && Arrays.equals(Files.readAllBytes(absolutePath), content.read())) {
          continue;
        }
      } catch (IOException ex) {
        logger.warn("Failed to read " + absolutePath, ex);
      }
      try {
        Files.deleteIfExists(absolutePath);
      } catch (IOException ex) {
        logger.warn("Failed to delete " + absolutePath, ex);
      }
      try {
        // Wait a little after deleting if not the first attempt.
        Thread.sleep(100 * attempt);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (Files.exists(absolutePath)) {
        logger.warn("File %s still exists".formatted(absolutePath));
      }
      try {
        Files.copy(content.openStream(), absolutePath, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ex) {
        logger.warn("Failed to copy to " + absolutePath, ex);
        lastException = ex;
        continue;
      }
      return; // success
    }
    if (lastException != null) {
      throw new BuildException(lastException);
    }
  }
}
