/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/** Static utility methods to help with target map builder construction. */
public class NbTargetMapUtils {

  /**
   * Parses a label to a path relative to the workspace.
   *
   * <p>For example, given blaze package "com.google.foo": Label "//com/google/bar/Bar.java" will
   * parse to "com/google/bar/Bar.java". Label "Foo.java" will parse to "com/google/foo/Foo.java".
   */
  public static String workspacePathForLabel(WorkspacePath blazePackage, String label) {
    if (label.startsWith("//")) {
      return label.substring(2);
    }
    return blazePackage + "/" + label;
  }

  /** Computes the blaze package of a target defined at the given label. */
  public static WorkspacePath blazePackageForLabel(String label) {
    return Label.create(label).blazePackage();
  }

  /** Returns a reference to a source artifact in the form of an {@link ArtifactLocation}. */
  public static ArtifactLocation makeSourceArtifact(String workspacePath) {
    return ArtifactLocation.builder().setRelativePath(workspacePath).setIsSource(true).build();
  }

  /** Create a label out of a relative path to the target or a string representing a label. */
  public static Label normalizeRelativePathOrLabel(
      String pathOrLabelString, WorkspacePath blazePackage) {
    if (pathOrLabelString.startsWith("//")) {
      return Label.create(pathOrLabelString);
    }
    return Label.create("//" + blazePackage + pathOrLabelString);
  }
}
