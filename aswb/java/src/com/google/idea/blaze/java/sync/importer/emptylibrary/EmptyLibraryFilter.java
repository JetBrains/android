/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.java.sync.importer.emptylibrary;

import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.common.experiments.IntExperiment;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Assumes that the passed {@link BlazeArtifact} is a JAR and checks whether the JAR is effectively
 * empty (i.e. it has nothing other than a manifest and directories)
 *
 * <p>Since this filter is used, in part, to determine which remote output JARs should be copied to
 * a local cache, checking the contents of those JARs can involve expensive network operations. We
 * try to minimize this cost by checking the JAR's size first and applying heuristics to avoid doing
 * extra work in the more obvious cases.
 */
public class EmptyLibraryFilter implements Predicate<BlazeArtifact> {
  private static final String FN_MANIFEST = "MANIFEST.MF";

  /**
   * Any JAR that is this size (in bytes) or smaller is assumed to be empty.
   *
   * <p>We came up with this number by checking the file size and contents for every JAR in the
   * .blaze/libraries directory for several projects (AGSA, AGMM, Express, Photos, Phonesky,
   * Memegen, and ASwB). This is the size of the largest empty JAR we saw that is still smaller than
   * all of the non-empty JARs we saw.
   */
  private static final IntExperiment presumedEmptyThresholdBytes =
      new IntExperiment("blaze.empty.jar.threshold", 359);
  /**
   * Any JAR that is this size (in bytes) or larger is assumed to be non-empty.
   *
   * <p>We came up with this number by checking the file size and contents for every JAR in the
   * .blaze/libraries directory for several projects (AGSA, AGMM, Express, Photos, Phonesky,
   * Memegen, and ASwB). This is the size of the smallest non-empty JAR we saw that is still larger
   * than all of the empty JARs we saw.
   */
  private static final IntExperiment presumedNonEmptyThresholdBytes =
      new IntExperiment("blaze.nonempty.jar.threshold", 470);

  private static final Logger logger = Logger.getInstance(EmptyLibraryFilter.class);

  EmptyLibraryFilter() {}

  static boolean isEnabled() {
    return Arrays.stream(EmptyLibraryFilterSettings.EP_NAME.getExtensions())
        .findFirst()
        .map(EmptyLibraryFilterSettings::isEnabled)
        .orElse(true);
  }

  @Override
  public boolean test(BlazeArtifact blazeLibrary) {
    if (!isEnabled()) {
      return false;
    }

    try {
      return isEmpty(blazeLibrary);
    } catch (IOException e) {
      logger.warn(e);
      return false; // If something went wrong reading the file, consider it non-empty
    }
  }
  
  /**
   * Returns true if the given JAR is effectively empty (i.e. it has nothing other than a manifest
   * and directories).
   */
  static boolean isEmpty(BlazeArtifact artifact) throws IOException {
    long length = artifact.getLength();
    if (length <= presumedEmptyThresholdBytes.getValue()) {
      // Note: this implicitly includes files that can't be found (length -1 or 0).
      return true;
    }
    if (length >= presumedNonEmptyThresholdBytes.getValue()) {
      return false;
    }
    try (InputStream inputStream = artifact.getInputStream();
        JarInputStream jarInputStream = new JarInputStream(inputStream)) {
      return isEmpty(jarInputStream);
    }
  }

  private static boolean isEmpty(JarInputStream jar) throws IOException {
    for (JarEntry entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
      if (!entry.isDirectory() && !entry.getName().endsWith(FN_MANIFEST)) {
        return false;
      }
    }
    return true;
  }
}
