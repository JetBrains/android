/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.deps;

import com.google.idea.blaze.qsync.project.ProjectPath;
import java.nio.file.Path;

/** Static helpers for managing directories in the project artifact store. */
public class ArtifactDirectories {

  private static final ProjectPath ROOT = ProjectPath.projectRelative(".bazel");

  /**
   * By default, all project artifacts go in this directory, at a path matching their bazel output
   * path.
   */
  public static final ProjectPath DEFAULT = ROOT.resolveChild(Path.of("buildout"));

  /**
   * Compiled dependency jar files have their own path, since the IDE uses all jars within this
   * directory and we want to ensure there are no extras there.
   */
  public static final ProjectPath JAVADEPS = ROOT.resolveChild(Path.of("javadeps"));

  public static final ProjectPath JAVA_GEN_SRC = ROOT.resolveChild(Path.of("gensrc/java"));
  public static final ProjectPath JAVA_GEN_TESTSRC = ROOT.resolveChild(Path.of("gensrc/javatests"));

  /** Generated CC headers go in the default directory. */
  public static final ProjectPath GEN_CC_HEADERS = DEFAULT;

  private ArtifactDirectories() {}

  /**
   * Constructs a project path for a given include dir flag value. This can then be used to ensure
   * that the flag passed to the IDE points to the correct location.
   */
  public static ProjectPath forCcInclude(String includeDir) {
    Path includePath = Path.of(includeDir);
    // include paths that refer to generated locations start with the `bazel-out` (or `blaze-out`)
    // component, so paths that start with that are resolved relative to the generated headers dir
    // in the project artifact store.
    if (includePath.startsWith("blaze-out") || includePath.startsWith("bazel-out")) {
      // Remove the bXXXX-out prefix since that is not present in the project artifact store where
      // generated headers are kept.
      return GEN_CC_HEADERS.resolveChild(includePath.getName(0).relativize(includePath));
    } else if (includePath.isAbsolute()) {
      return ProjectPath.absolute(includePath);
    } else {
      return ProjectPath.WORKSPACE_ROOT.resolveChild(includePath);
    }
  }
}
