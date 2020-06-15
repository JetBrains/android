// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.testing;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class ModuleNames {
  @NotNull private final Project myProject;

  public ModuleNames(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Transforms "almost qualified" module name either into simple module name (if {@link GradleProjectSettings#isUseQualifiedModuleNames()}
   * reports {@code false}), or qualified module name. "Almost qualified" module name may omit project name as the first segment
   * for ease of test writing.
   * <p>
   * Examples: <br>
   * "app.lib" -> "MyProject.app.lib" or "lib" <br>
   * "MyProject.app.lib" -> "MyProject.app.lib" or "lib" <br>
   * "app" -> "MyProject.app" or "app"
   *
   * @param qualifiedModuleName "Almost qualified" module name. May omit project name as the first segment for ease of test writing.
   * @return qualified or simple module name, depending on {@link GradleProjectSettings#isUseQualifiedModuleNames()}
   */
  public String transformIfNeeded(String qualifiedModuleName) {
    GradleProjectSettings projectSettings = GradleSettings.getInstance(myProject)
      .getLinkedProjectSettings(myProject.getBasePath());
    if (projectSettings != null) {
      if (projectSettings.isUseQualifiedModuleNames()) {
        if (!qualifiedModuleName.startsWith(myProject.getName())) {
          return myProject.getName() + "." + qualifiedModuleName;
        }
      }
      else {
        return qualifiedModuleName.substring(qualifiedModuleName.lastIndexOf('.') + 1);
      }
    }

    return qualifiedModuleName;
  }
}
