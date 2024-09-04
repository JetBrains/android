/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.settings;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;

/** Blaze project utilities. */
public class Blaze {

  private Blaze() {}

  /**
   * Returns whether this project was imported from blaze.
   *
   * @deprecated use {@link #getProjectType(Project)}.
   */
  @Deprecated
  public static boolean isBlazeProject(@Nullable Project project) {
    return project != null
        && BlazeImportSettingsManager.getInstance(project).getImportSettings() != null;
  }

  /**
   * Returns the ProjectType of this imported project. {@code ProjectType.UNKNOWN} will be returned
   * if the project is not available, not imported from blaze, or we failed to access its import
   * settings.
   */
  public static ProjectType getProjectType(@Nullable Project project) {
    if (project == null) {
      return ProjectType.UNKNOWN;
    }

    BlazeImportSettings blazeImportSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (blazeImportSettings == null) {
      return ProjectType.UNKNOWN;
    }
    return blazeImportSettings.getProjectType();
  }

  /**
   * Returns the build system associated with this project, or falls back to the default blaze build
   * system if the project is null or not a blaze project.
   */
  public static BuildSystemName getBuildSystemName(@Nullable Project project) {
    BlazeImportSettings importSettings =
        project == null
            ? null
            : BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return BuildSystemProvider.defaultBuildSystem().buildSystem();
    }
    return importSettings.getBuildSystem();
  }

  /**
   * Returns the build system provider associated with this project, or falls back to the default
   * blaze build system if the project is null or not a blaze project.
   */
  public static BuildSystemProvider getBuildSystemProvider(@Nullable Project project) {
    BuildSystemProvider provider =
        BuildSystemProvider.getBuildSystemProvider(getBuildSystemName(project));
    return provider != null ? provider : BuildSystemProvider.defaultBuildSystem();
  }

  /**
   * The name of the build system associated with the given project, or falls back to the default
   * blaze build system if the project is null or not a blaze project.
   */
  public static String buildSystemName(@Nullable Project project) {
    return getBuildSystemName(project).getName();
  }

  /** The default build system */
  public static BuildSystemName defaultBuildSystem() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem();
  }

  /**
   * The name of the application-wide build system default. This should only be used in situations
   * where it doesn't make sense to use the build system associated with the current project (e.g.
   * the import project action).
   */
  public static String defaultBuildSystemName() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem().getName();
  }

  /**
   * Tries to guess the current project, and uses that to determine the build system name.<br>
   * Should only be used in situations where the current project is not accessible.
   */
  public static String guessBuildSystemName() {
    Project project = guessCurrentProject();
    return buildSystemName(project);
  }

  private static Project guessCurrentProject() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 1) {
      return openProjects[0];
    }
    if (SwingUtilities.isEventDispatchThread()) {
      return (Project) DataManager.getInstance().getDataContext().getData("project");
    }
    return null;
  }
}
