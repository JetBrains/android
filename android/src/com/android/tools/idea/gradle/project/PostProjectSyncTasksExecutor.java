/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.GradleImportNotificationListener;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.ProjectBuilder;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.rendering.ProjectResourceRepository;
import com.android.tools.idea.sdk.DefaultSdks;
import com.android.tools.idea.startup.AndroidStudioSpecificInitializer;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.List;

public class PostProjectSyncTasksExecutor {
  @NotNull private final Project myProject;

  private static final boolean DEFAULT_GENERATE_SOURCES_AFTER_SYNC = true;

  private volatile boolean myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;

  @NotNull
  public static PostProjectSyncTasksExecutor getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PostProjectSyncTasksExecutor.class);
  }

  public PostProjectSyncTasksExecutor(@NotNull Project project) {
    myProject = project;
  }

  public void onProjectSetupCompletion() {
    ensureAllModulesHaveSdk();
    Projects.enforceExternalBuild(myProject);

    if (AndroidStudioSpecificInitializer.isAndroidStudio()) {
      // We remove modules not present in settings.gradle in Android Studio only. IDEA allows to have non-Gradle modules in Gradle projects.
      removeModulesNotInGradleSettingsFile();
    }
    else {
      AndroidGradleProjectComponent.getInstance(myProject).checkForSupportedModules();
    }

    BuildVariantView.getInstance(myProject).updateContents();
    GradleImportNotificationListener.updateLastSyncTimestamp(myProject);
    EditorNotifications.getInstance(myProject).updateAllNotifications();
    ProjectResourceRepository.moduleRootsChanged(myProject);

    if (myGenerateSourcesAfterSync) {
      ProjectBuilder.getInstance(myProject).generateSourcesOnly();
    } else {
      // set default value back.
      myGenerateSourcesAfterSync = DEFAULT_GENERATE_SOURCES_AFTER_SYNC;
    }
  }

  private void ensureAllModulesHaveSdk() {
    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    for (Module module : moduleManager.getModules()) {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      ModifiableRootModel model = moduleRootManager.getModifiableModel();
      try {
        if (model.getSdk() == null) {
          Sdk jdk = DefaultSdks.getDefaultJdk();
          model.setSdk(jdk);
        }
      }
      finally {
        model.commit();
      }
    }
  }

  private void removeModulesNotInGradleSettingsFile() {
    GradleSettingsFile gradleSettingsFile = GradleSettingsFile.get(myProject);
    final List<Module> modulesToRemove = Lists.newArrayList();

    final ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    Module[] modules = moduleManager.getModules();

    if (gradleSettingsFile == null) {
      // If there is no settings.gradle file, it means that the top-level module is the only module recognized by Gradle.
      if (modules.length == 1) {
        return;
      }
      boolean topLevelModuleFound = false;
      for (Module module : modules) {
        if (!topLevelModuleFound && isTopLevel(module)) {
          topLevelModuleFound = true;
        }
        else {
          modulesToRemove.add(module);
        }
      }
    }
    else {
      for (Module module : modules) {
        if (isNonGradleModule(module) || isOrphanGradleModule(module, gradleSettingsFile)) {
          modulesToRemove.add(module);
        }
      }
    }

    if (!modulesToRemove.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ModifiableModuleModel moduleModel = moduleManager.getModifiableModel();
          try {
            for (Module module : modulesToRemove) {
              removeDependencyLinks(module, moduleManager);
              moduleModel.disposeModule(module);
            }
          }
          finally {
            moduleModel.commit();
          }
        }
      });
    }
  }

  private static boolean isNonGradleModule(@NotNull Module module) {
    ModuleType moduleType = ModuleType.get(module);
    if (moduleType instanceof JavaModuleType) {
      String externalSystemId = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
      return !GradleConstants.SYSTEM_ID.getId().equals(externalSystemId);
    }
    return false;
  }

  private static boolean isOrphanGradleModule(@NotNull Module module, @NotNull GradleSettingsFile settingsFile) {
    if (isTopLevel(module)) {
      return false;
    }
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    if (facet == null) {
      return true;
    }
    String gradleProjectPath = facet.getConfiguration().GRADLE_PROJECT_PATH;
    Iterable<String> allModules = settingsFile.getModules();
    return !Iterables.contains(allModules, gradleProjectPath);
  }

  private static boolean isTopLevel(@NotNull Module module) {
    AndroidGradleFacet facet = AndroidGradleFacet.getInstance(module);
    if (facet == null) {
      // if this is the top-level module, it may not have the Gradle facet but it is still valid, because it represents the project.
      String moduleRootDirPath = new File(FileUtil.toSystemDependentName(module.getModuleFilePath())).getParent();
      return moduleRootDirPath.equals(module.getProject().getBasePath());
    }
    String gradleProjectPath = facet.getConfiguration().GRADLE_PROJECT_PATH;
    // top-level modules have Gradle path ":"
    return SdkConstants.GRADLE_PATH_SEPARATOR.equals(gradleProjectPath);
  }

  private static void removeDependencyLinks(@NotNull Module module, @NotNull ModuleManager moduleManager) {
    List<Module> dependents = moduleManager.getModuleDependentModules(module);
    for (Module dependent : dependents) {
      if (dependent.isDisposed()) {
        continue;
      }
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(dependent);
      ModifiableRootModel modifiableModel = moduleRootManager.getModifiableModel();
      try {
        for (OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
          if (orderEntry instanceof ModuleOrderEntry) {
            Module orderEntryModule = ((ModuleOrderEntry)orderEntry).getModule();
            if (module.equals(orderEntryModule)) {
              modifiableModel.removeOrderEntry(orderEntry);
            }
          }
        }
      }
      finally {
        modifiableModel.commit();
      }
    }
  }

  public void setGenerateSourcesAfterSync(boolean generateSourcesAfterSync) {
    myGenerateSourcesAfterSync = generateSourcesAfterSync;
  }
}
