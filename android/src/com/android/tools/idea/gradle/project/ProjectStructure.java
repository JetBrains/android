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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.GuardedBy;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_INSTANTAPP;

public class ProjectStructure {
  @NotNull private static final ModuleFinder EMPTY = new ModuleFinder();

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
    return ServiceManager.getService(project, ProjectStructure.class);
  }

  public ProjectStructure(@NotNull Project project) {
    myProject = project;
  }

  public void analyzeProjectStructure(@NotNull ProgressIndicator progressIndicator) {
    AndroidPluginVersionsInProject pluginVersionsInProject = new AndroidPluginVersionsInProject();

    List<Module> appModules = new ArrayList<>();

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<Module> modules = Arrays.asList(moduleManager.getModules());
    List<Module> leafModules = new ArrayList<>(modules);

    ModuleFinder moduleFinder = new ModuleFinder();

    JobLauncher jobLauncher = JobLauncher.getInstance();
    jobLauncher.invokeConcurrentlyUnderProgress(modules, progressIndicator, true /* fail fast */, module -> {
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
        else {
          JavaFacet javaFacet = JavaFacet.getInstance(module);
          if (javaFacet != null && !javaFacet.getConfiguration().BUILDABLE) {
            // Remove module not "buildable" from "leaf" modules.
            leafModules.remove(module);
          }
          return true;
        }
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        leafModules.removeAll(Arrays.asList(rootManager.getDependencies()));
      }
      else {
        // Remove non-Gradle modules from "leaf" modules.
        leafModules.remove(module);
      }
      return true;
    });

    synchronized (myLock) {
      myPluginVersionsInProject.copy(pluginVersionsInProject);

      // "Leaf" modules include app modules and the non-app modules that no other modules depend on.
      myLeafModules.clear();
      myLeafModules.addAll(leafModules);

      myAppModules.clear();
      myAppModules.addAll(appModules);

      myModuleFinderRef.set(moduleFinder);
    }
  }

  private static boolean isApp(@NotNull AndroidModuleModel androidModel) {
    int projectType = androidModel.getAndroidProject().getProjectType();
    return projectType == PROJECT_TYPE_APP || projectType == PROJECT_TYPE_INSTANTAPP;
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
  public ImmutableList<Module> getAppModules() {
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

  public static class AndroidPluginVersionsInProject {
    @NotNull private final Map<String, GradleVersion> myAgpVersionsPerModule = new HashMap<>();

    void copy(@NotNull AndroidPluginVersionsInProject other) {
      myAgpVersionsPerModule.putAll(other.myAgpVersionsPerModule);
    }

    void add(@NotNull String gradlePath, @NotNull AndroidModuleModel androidModel) {
      GradleVersion modelVersion = androidModel.getModelVersion();
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
