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
package com.google.idea.blaze.android.projectsystem;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Build systems can implement their own {@link DesugaringLibraryConfigFilesLocator} to help the IDE
 * to locate desugaring config files. Note that there can be multiple locators enabled at the same
 * time; see {@link DesugaringLibraryConfigFilesLocator#forBuildSystem(BuildSystemName)} on how to
 * obtain them for a given build system.
 */
public interface DesugaringLibraryConfigFilesLocator {
  ExtensionPointName<DesugaringLibraryConfigFilesLocator> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.DesugaringLibraryConfigFilesLocator");

  /** Returns true if desugaring library config files exist. */
  public boolean getDesugarLibraryConfigFilesKnown();

  /** Returns the list of paths to the desugaring library config files */
  ImmutableList<Path> getDesugarLibraryConfigFiles(Project project);

  /**
   * Returns the {@link BuildSystemName} this {@link DesugaringLibraryConfigFilesLocator} supports.
   */
  BuildSystemName buildSystem();

  /**
   * Returns an {@link ImmutableList} of {@link DesugaringLibraryConfigFilesLocator} that supports
   * the given build system.
   */
  static ImmutableList<DesugaringLibraryConfigFilesLocator> forBuildSystem(
      BuildSystemName buildSystemName) {
    return Arrays.stream(EP_NAME.getExtensions())
        .filter(provider -> provider.buildSystem().equals(buildSystemName))
        .collect(toImmutableList());
  }
}
