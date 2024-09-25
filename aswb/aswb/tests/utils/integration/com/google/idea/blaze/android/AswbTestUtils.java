/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.google.idea.blaze.android;

import static java.util.Objects.requireNonNull;

import com.android.tools.idea.util.StudioPathManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class AswbTestUtils {
  /** {@link com.google.idea.testing.BlazeTestSystemPropertiesRule#configureSystemProperties()} */
  public static final String SANDBOX_IDEA_HOME = "_intellij_test_sandbox/home/";

  private AswbTestUtils() {}

  public static void symlinkToSandboxHome(String target, String customLink) {
    try {
      Path linkLocation = StudioPathManager.resolvePathFromSourcesRoot(customLink);
      Path linkTarget = getRunfilesWorkspaceRoot().toPath().resolve(customLink);
      if (Files.exists(linkLocation) && Objects.equals(Files.readSymbolicLink(linkLocation), linkTarget)) {
        return;
      }
      Files.createDirectories(linkLocation.getParent());
      Files.createSymbolicLink(linkLocation, linkTarget);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static synchronized File getRunfilesWorkspaceRoot() {
    // Use the sandboxed root provided for bazel tests.
    String workspace = requireNonNull(System.getenv("TEST_WORKSPACE"));
    String workspaceParent = requireNonNull(System.getenv("TEST_SRCDIR"));
    return new File(workspaceParent, workspace);
  }
}
