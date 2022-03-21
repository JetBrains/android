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

import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getHolderModule;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class ProjectStructure {
  @NotNull private final Project myProject;

  @NotNull private final Object myLock = new Object();

  @GuardedBy("myLock")
  @NotNull
  private final AndroidPluginVersionsInProject myPluginVersionsInProject = new AndroidPluginVersionsInProject();

  @GuardedBy("myLock")
  @NotNull
  private final List<Module> myAppModules = new ArrayList<>();

  @GuardedBy("myLock")
  @NotNull
  private final List<Module> myLeafModules = new ArrayList<>();

  @NotNull
  public static ProjectStructure getInstance(@NotNull Project project) {
    return project.getService(ProjectStructure.class);
  }

  private ProjectStructure(@NotNull Project project) {
    myProject = project;
  }

  public void analyzeProjectStructure() {
    AndroidPluginVersionsInProject pluginVersionsInProject = new AndroidPluginVersionsInProject();

    Queue<Module> appModules = new ConcurrentLinkedQueue<>();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] modules = moduleManager.getModules();

    Set<Module> accessibleModules = Arrays.stream(modules)
      .filter(it -> ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, it))
      .flatMap(it -> Arrays.stream(ModuleRootManager.getInstance(it).getDependencies()))
      .collect(Collectors.toSet());

    List<Module> leafModules =
      Arrays.stream(modules)
        .filter(it -> ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, it))
        .filter(it -> !accessibleModules.contains(it) || isAppOrFeature(it))
        .map(ModuleSystemUtil::getHolderModule)
        .distinct()
        .collect(Collectors.toList());

    for (Module module : modules) {
      if (getHolderModule(module) != module) continue;
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          pluginVersionsInProject.add(androidModel);
          if (isApp(module)) {
            appModules.add(module);
          }
        }
      }
    }

    synchronized (myLock) {
      myPluginVersionsInProject.copy(pluginVersionsInProject);

      // "Leaf" modules include app modules and the non-app modules that no other modules depend on.
      myLeafModules.clear();
      myLeafModules.addAll(leafModules);

      myAppModules.clear();
      myAppModules.addAll(appModules);
    }
  }

  private static boolean isAppOrFeature(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null && facet.getConfiguration().isAppOrFeature();
  }

  private static boolean isApp(@NotNull Module module) {
    AndroidModuleSystem.Type projectType = getModuleSystem(module).getType();
    return projectType == AndroidModuleSystem.Type.TYPE_APP || projectType == AndroidModuleSystem.Type.TYPE_INSTANTAPP;
  }

  @NotNull
  public AndroidPluginVersionsInProject getAndroidPluginVersions() {
    AndroidPluginVersionsInProject pluginVersionsInProject = new AndroidPluginVersionsInProject();
    synchronized (myLock) {
      pluginVersionsInProject.copy(myPluginVersionsInProject);
    }
    return pluginVersionsInProject;
  }

  @NotNull
  public ImmutableList<Module> getAppHolderModules() {
    synchronized (myLock) {
      return ImmutableList.copyOf(myAppModules);
    }
  }

  /**
   * @return the project's app modules and the modules that no other modules depend on.
   */
  @NotNull
  public ImmutableList<Module> getLeafModules() {
    synchronized (myLock) {
      return ImmutableList.copyOf(myLeafModules);
    }
  }

  public void clearData() {
    synchronized (myLock) {
      myPluginVersionsInProject.clear();
      myAppModules.clear();
      myLeafModules.clear();
    }
  }

  public static class AndroidPluginVersionsInProject {
    @NotNull private final Set<GradleVersion> myAgpVersions = new HashSet<>();

    void copy(@NotNull AndroidPluginVersionsInProject other) {
      myAgpVersions.addAll(other.myAgpVersions);
    }

    void add(@NotNull AndroidModuleModel androidModel) {
      GradleVersion modelVersion = androidModel.getAgpVersion();
      if (modelVersion != null) {
        add(modelVersion);
      }
    }

    @VisibleForTesting
    void add(@NotNull GradleVersion modelVersion) {
      myAgpVersions.add(modelVersion);
    }

    void clear() {
      myAgpVersions.clear();
    }

    @NotNull
    public List<GradleVersion> getAllVersions() {
      return new ArrayList<>(myAgpVersions);
    }
  }
}
