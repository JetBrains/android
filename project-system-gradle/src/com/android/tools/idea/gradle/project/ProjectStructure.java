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
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.GradleVersion.AgpVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
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
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.text.StringsKt;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @GuardedBy("myLock")
  @NotNull
  private final List<Module> myLeafHolderModules = new ArrayList<>();

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
    List<Module> mainModules = Arrays.stream(moduleManager.getModules())
      .filter(ProjectStructure::isAndroidOrJavaMainSourceSetModuleBySourceSetName)
      .collect(Collectors.toList());

    Set<Module> mainModulesAccessibleFromMainModules = mainModules.stream()
      .flatMap(it -> Arrays.stream(ModuleRootManager.getInstance(it).getDependencies()))
      .filter(ProjectStructure::isAndroidOrJavaMainSourceSetModuleBySourceSetName)
      .collect(Collectors.toSet());

    List<Module> leafHolderModules =
      mainModules.stream()
        .filter(it -> !mainModulesAccessibleFromMainModules.contains(it) || isAppOrFeature(it))
        .map(ProjectStructure::getHolder)
        .collect(Collectors.toList());

    List<Module> appHolderModules =
      mainModules.stream()
        .filter(ProjectStructure::isApp)
        .map(ProjectStructure::getHolder)
        .collect(Collectors.toList());

    for (Module module : mainModules) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        pluginVersionsInProject.add(androidModel);
      }
    }

    synchronized (myLock) {
      myPluginVersionsInProject = pluginVersionsInProject;

      // "Leaf" modules include app modules and the non-app modules that no other modules depend on via any path that starts from a main
      // source setand and end on a main source set.
      myLeafHolderModules.clear();
      myLeafHolderModules.addAll(leafHolderModules);

      myAppHolderModules.clear();
      myAppHolderModules.addAll(appHolderModules);
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

  /**
   * @return the project's app modules and the modules that no other modules depend on.
   */
  @NotNull
  public ImmutableList<Module> getLeafHolderModules() {
    synchronized (myLock) {
      return ImmutableList.copyOf(myLeafHolderModules);
    }
  }

  @Nullable
  public static Module getHolder(@NotNull Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return null;
    if (GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module))) {
      String moduleName = module.getName();
      int lastDot  = moduleName.lastIndexOf('.');
      if (lastDot > 0) {
        String holderModuleName = moduleName.substring(0, lastDot);
        Module holder = ModuleManager.getInstance(module.getProject()).findModuleByName(holderModuleName);
        if (holder != null) return holder;
      }
    }
    return module;
  }

  private static boolean isAndroidOrJavaMainSourceSetModuleBySourceSetName(@NotNull Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;
    if (!GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY.equals(ExternalSystemApiUtil.getExternalModuleType(module))) return false;
    String moduleId = ExternalSystemApiUtil.getExternalProjectId(module);
    if (moduleId == null) return false;
    return "main".equals(StringsKt.substringAfterLast(moduleId, ":", ""));
  }

  public static boolean isAndroidOrJavaHolderModule(@NotNull Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) return false;
    return ExternalSystemApiUtil.getExternalModuleType(module) == null;
  }

  public static class AndroidPluginVersionsInProject {
    @NotNull private final Set<AgpVersion> myAgpVersions = new HashSet<>();

    private void add(@NotNull AndroidModuleModel androidModel) {
      AgpVersion modelVersion = androidModel.getAgpVersion();
      if (modelVersion != null) {
        add(modelVersion);
      }
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
