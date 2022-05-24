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

import static com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder.EMPTY;
import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getHolderModule;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
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
  private final AndroidPluginVersionsInProject myPluginVersionsInProject = new AndroidPluginVersionsInProject();

  @GuardedBy("myLock")
  @NotNull
  private final List<Module> myAppModules = new ArrayList<>();

  @GuardedBy("myLock")
  @NotNull
  private final List<Module> myLeafModules = new ArrayList<>();

  @GuardedBy("myLock")
  @NotNull
  private final Ref<ModuleFinder> myModuleFinderRef = new Ref<>(EMPTY);

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
    ModuleFinder moduleFinder = new ModuleFinder(myProject);
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

    for (Module module : modules) {
      if (getHolderModule(module) != module) continue;
      GradleFacet gradleFacet = GradleFacet.getInstance(module);
      if (gradleFacet != null) {
        String gradlePath = gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        moduleFinder.addModule(module, gradlePath);

        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          pluginVersionsInProject.add(gradlePath, androidModel);
          if (isApp(androidModel)) {
            appModules.add(module);
          }
        }
      }
    }

    synchronized (myLock) {
      myPluginVersionsInProject.copy(pluginVersionsInProject);

      // "Leaf" modules include app modules and the non-app modules that no other modules depend on.
      myLeafModules.clear();
      myLeafModules.addAll(leafHolderModules);

      myAppModules.clear();
      myAppModules.addAll(appModules);

      myModuleFinderRef.set(moduleFinder);
    }
  }

  private static boolean isAppOrFeature(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    return facet != null && facet.getConfiguration().isAppOrFeature();
  }

  private static boolean isApp(@NotNull AndroidModuleModel androidModel) {
    IdeAndroidProjectType projectType = androidModel.getAndroidProject().getProjectType();
    return projectType == IdeAndroidProjectType.PROJECT_TYPE_APP || projectType == IdeAndroidProjectType.PROJECT_TYPE_INSTANTAPP;
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

  @NotNull
  public ModuleFinder getModuleFinder() {
    synchronized (myLock) {
      return myModuleFinderRef.get();
    }
  }

  public void clearData() {
    synchronized (myLock) {
      myPluginVersionsInProject.clear();
      myAppModules.clear();
      myLeafModules.clear();
      myModuleFinderRef.set(EMPTY);
    }
  }

  @Nullable
  private static Module getHolder(@NotNull Module module) {
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

  public static class AndroidPluginVersionsInProject {
    @NotNull private final Map<String, GradleVersion> myAgpVersionsPerModule = new HashMap<>();

    void copy(@NotNull AndroidPluginVersionsInProject other) {
      myAgpVersionsPerModule.putAll(other.myAgpVersionsPerModule);
    }

    void add(@NotNull String gradlePath, @NotNull AndroidModuleModel androidModel) {
      GradleVersion modelVersion = androidModel.getAgpVersion();
      if (modelVersion != null) {
        add(gradlePath, modelVersion);
      }
    }

    @VisibleForTesting
    void add(@NotNull String gradlePath, @NotNull GradleVersion modelVersion) {
      myAgpVersionsPerModule.put(gradlePath, modelVersion);
    }

    void clear() {
      myAgpVersionsPerModule.clear();
    }

    public boolean haveVersionsChanged(@NotNull AndroidPluginVersionsInProject other) {
      // If it's empty it could be because this is the first sync.
      if (!other.isEmpty()) {
        if (myAgpVersionsPerModule.size() != other.myAgpVersionsPerModule.size()) {
          return true;
        }
        for (Map.Entry<String, GradleVersion> entry : myAgpVersionsPerModule.entrySet()) {
          String modulePath = entry.getKey();
          GradleVersion otherAgpVersion = other.myAgpVersionsPerModule.get(modulePath);
          if (otherAgpVersion == null || entry.getValue().compareTo(otherAgpVersion) != 0) {
            return true;
          }
        }
      }
      return false;
    }

    @NotNull
    public List<GradleVersion> getAllVersions() {
      return myAgpVersionsPerModule.values().stream().distinct().collect(Collectors.toList());
    }

    boolean isEmpty() {
      return myAgpVersionsPerModule.isEmpty();
    }

    @VisibleForTesting
    @NotNull
    ImmutableMap<String, GradleVersion> getInternalMap() {
      return ImmutableMap.copyOf(myAgpVersionsPerModule);
    }
  }
}
