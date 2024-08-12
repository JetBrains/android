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
package com.google.idea.blaze.android.projectsystem;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Build systems can implement their own {@link MavenArtifactLocator} to help the IDE to locate
 * artifacts referenced using {@link GradleCoordinate}. Note that there can be multiple artifact
 * locators enabled at the same time; see {@link
 * MavenArtifactLocator#forBuildSystem(BuildSystemName)} on how to obtain them for a given build
 * system.
 */
public interface MavenArtifactLocator {
  ExtensionPointName<MavenArtifactLocator> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.MavenArtifactLocator");

  /**
   * Returns a label for the artifact referenced by the given coordinate if the artifact can be
   * located in the current workspace.
   *
   * @param coordinate GradleCoordinate for the artifact.
   */
  Label labelFor(GradleCoordinate coordinate);

  /** Returns the {@link BuildSystemName} this {@link MavenArtifactLocator} supports. */
  BuildSystemName buildSystem();

  /**
   * Returns an {@link ImmutableList} of {@link MavenArtifactLocator} that supports the given build
   * system.
   */
  static List<MavenArtifactLocator> forBuildSystem(BuildSystemName buildSystemName) {
    return Arrays.stream(EP_NAME.getExtensions())
        .filter(provider -> provider.buildSystem().equals(buildSystemName))
        .collect(Collectors.toList());
  }
}
