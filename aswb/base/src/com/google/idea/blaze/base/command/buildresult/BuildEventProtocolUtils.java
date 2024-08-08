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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.UUID;

/**
 * Utility methods for accessing blaze build event data via the build event protocol (BEP for
 * short).
 */
public final class BuildEventProtocolUtils {

  // Instructs BEP to use local file paths (file://...) rather than objfs blobids.
  private static final String LOCAL_FILE_PATHS = "--nobuild_event_binary_file_path_conversion";
  // A vm option overriding the directory used for the BEP output file.
  private static final String BEP_OUTPUT_FILE_VM_OVERRIDE = "bazel.bep.path";

  private BuildEventProtocolUtils() {}

  /**
   * Creates a temporary output file to write the BEP data to. Callers are responsible for deleting
   * this file after use.
   *
   * <p>Note: when mdproxy is in use, the file returned will be on an sshfs mounted filesystem, so
   * is shared between the local and mdproxy hosts. As such, this method should *not* be used for
   * files that are used only internally by the IDE, and using an sshfs filesystem in that case will
   * slow things down.
   */
  public static File createTempOutputFile() {
    File tempDir = getOutputDir();
    String suffix = UUID.randomUUID().toString();
    String fileName = "intellij-bep-" + suffix;
    File tempFile = new File(tempDir, fileName);
    // Callers should delete this file immediately after use. Add a shutdown hook as well, in case
    // the application exits before then.
    tempFile.deleteOnExit();
    return tempFile;
  }

  /** Returns a build flag instructing blaze to write build events to the given output file. */
  public static ImmutableList<String> getBuildFlags(File outputFile) {
    return ImmutableList.of("--build_event_binary_file=" + outputFile.getPath(), LOCAL_FILE_PATHS);
  }

  /**
   * Returns a directory for temporary files.
   *
   * <p>Note: when mdproxy is in use, the directory returned will be on an sshfs mounted filesystem,
   * so is shared between the local and mdproxy hosts. As such, this method should *not* be used for
   * files that are used only internally by the IDE, and using an sshfs filesystem in that case will
   * slow things down.
   */
  public static File getOutputDir() {
    String dirPath = System.getProperty(BEP_OUTPUT_FILE_VM_OVERRIDE);
    if (Strings.isNullOrEmpty(dirPath)) {
      dirPath = System.getProperty("java.io.tmpdir");
    }
    return new File(dirPath);
  }
}
