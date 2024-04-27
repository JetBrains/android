/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.repository.AgpVersion;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class ProjectStructure {
  @NotNull private final Project myProject;

  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull
  private AndroidPluginVersionsInProject myPluginVersionsInProject = new AndroidPluginVersionsInProject();

  @GuardedBy("myLock")
  @NotNull
  private final List<Module> myAppHolderModules = new ArrayList<>();

  @NotNull
  public static ProjectStructure getInstance(@NotNull Project project) {
    return project.getService(ProjectStructure.class);
  }

  private ProjectStructure(@NotNull Project project) {
    myProject = project;
  }

  public void analyzeProjectStructure() {
    AndroidPluginVersionsInProject pluginVersionsInProject = new AndroidPluginVersionsInProject();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<Module> appHolderModules = new ArrayList<>();
    for (Module module : moduleManager.getModules()) {
      GradleAndroidModel androidModel = GradleAndroidModel.get(module);
      if (androidModel != null) {
        pluginVersionsInProject.add(androidModel);
        if (isApp(module) && ModuleSystemUtil.isHolderModule(module)) {
          appHolderModules.add(module);
        }
      }
    }

    synchronized (myLock) {
      myPluginVersionsInProject = pluginVersionsInProject;

      myAppHolderModules.clear();
      myAppHolderModules.addAll(appHolderModules);
    }
  }

  private static boolean isApp(@NotNull Module module) {
    AndroidModuleSystem.Type projectType = getModuleSystem(module).getType();
    return projectType == AndroidModuleSystem.Type.TYPE_APP || projectType == AndroidModuleSystem.Type.TYPE_INSTANTAPP;
  }

  @NotNull
  public AndroidPluginVersionsInProject getAndroidPluginVersions() {
    synchronized (myLock) {
      return myPluginVersionsInProject;
    }
  }

  @NotNull
  public ImmutableList<Module> getAppHolderModules() {
    synchronized (myLock) {
      return ImmutableList.copyOf(myAppHolderModules);
    }
  }

  public static boolean isAndroidOrJavaHolderModule(@NotNull Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;
    return ExternalSystemApiUtil.getExternalModuleType(module) == null;
  }

  public static class AndroidPluginVersionsInProject {
    @NotNull private final Set<AgpVersion> myAgpVersions = new HashSet<>();

    private void add(@NotNull GradleAndroidModel androidModel) {
      AgpVersion modelVersion = androidModel.getAgpVersion();
      add(modelVersion);
    }

    @VisibleForTesting
    private void add(@NotNull AgpVersion modelVersion) {
      myAgpVersions.add(modelVersion);
    }

    @NotNull
    public List<AgpVersion> getAllVersions() {
      return new ArrayList<>(myAgpVersions);
    }
  }
}
