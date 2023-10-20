/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import static com.intellij.openapi.actionSystem.LangDataKeys.MODULE_CONTEXT_ARRAY;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static org.jetbrains.android.facet.AndroidRootUtil.findModuleRootFolderPath;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.DataManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleProjectInfo {
  @NotNull private final Project myProject;
  private volatile boolean myNewProject;
  /**
   * See <a href="https://issuetracker.google.com/291935296">this bug</a> for more info.
   * <p>
   * This field, related getters and setters and their usages need to be maintained for the time being
   * since the gradle-profiler intelliJ IDEA plugin still rely on them.
   */
  private volatile boolean mySkipStartupActivity;

  @NotNull
  public static GradleProjectInfo getInstance(@NotNull Project project) {
    return project.getService(GradleProjectInfo.class);
  }

  public GradleProjectInfo(@NotNull Project project) {
    myProject = project;
  }

  public boolean isNewProject() {
    return myNewProject;
  }

  public void setNewProject(boolean newProject) {
    myNewProject = newProject;
  }

  @Deprecated
  public boolean isSkipStartupActivity() {
    return mySkipStartupActivity;
  }

  @Deprecated
  public void setSkipStartupActivity(boolean skipStartupActivity) {
    mySkipStartupActivity = skipStartupActivity;
  }

  /**
   * @return the modules in a Gradle-based project that contain an {@code AndroidFacet}.
   */
  @NotNull
  public List<Module> getAndroidModules() {
    ImmutableList.Builder<Module> modules = ImmutableList.builder();

    ReadAction.run(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      for (Module module : ProjectSystemUtil.getAndroidModulesForDisplay(myProject, null)) {
        if (AndroidFacet.getInstance(module) != null && GradleFacet.getInstance(module) != null) {
          modules.add(module);
        }
      }
    });
    return modules.build();
  }
}
