/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.fd.InstantRunGradleUtils;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static com.android.tools.idea.gradle.util.GradleUtil.getDependencies;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleVersion;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Tracks, using {@link UsageTracker}, the structure of a project.
 */
class ProjectStructureUsageTracker {
  private static final Logger LOG = Logger.getInstance(ProjectStructureUsageTracker.class);

  @NotNull private final Project myProject;

  ProjectStructureUsageTracker(@NotNull Project project) {
    myProject = project;
  }

  void trackProjectStructure() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      try {
        trackProjectStructure(moduleManager.getModules());
      }
      catch (Throwable e) {
        // Any errors in project tracking should not be displayed to the user.
        LOG.warn("Failed to track project structure", e);
      }
    });
  }

  private void trackProjectStructure(@NotNull Module[] modules) {
    AndroidGradleModel appModel = null;
    AndroidGradleModel libModel = null;

    int appCount = 0;
    int libCount = 0;

    for (Module module : modules) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        AndroidProject androidProject = androidModel.getAndroidProject();
        if (androidProject.isLibrary()) {
          libModel = androidModel;
          libCount++;
          continue;
        }
        appModel = androidModel;
        appCount++;
        trackExternalDependenciesInAndroidApp(androidModel);
      }
    }

    // Ideally we would like to get data from an "app" module, but if the project does not have one (which would be unusual, we can use
    // an Android library one.)
    AndroidGradleModel model = appModel != null ? appModel : libModel;
    if (model != null) {
      String appId = model.getApplicationId();
      AndroidProject androidProject = model.getAndroidProject();
      GradleVersion gradleVersion = getGradleVersion(myProject);

      ImmutableMap<String, String> irSettings = ImmutableMap.<String, String>builder()
        .put("userEnabledIr", Boolean.toString(InstantRunSettings.isInstantRunEnabled()))
        .put("modelSupportsIr", Boolean.toString(InstantRunGradleUtils.modelSupportsInstantRun(model)))
        .put("variantSupportsIr", Boolean.toString(InstantRunGradleUtils.variantSupportsInstantRun(model)))
        .build();
      UsageTracker.getInstance().trackGradleArtifactVersions(appId,
                                                             androidProject.getModelVersion(),
                                                             gradleVersion != null ? gradleVersion.toString() : "<Not Found>",
                                                             irSettings);
      UsageTracker.getInstance().trackModuleCount(appId, modules.length, appCount, libCount);

      for (Module module : modules) {
        AndroidGradleModel androidModel = AndroidGradleModel.get(module);
        if (androidModel != null) {
          UsageTracker.getInstance().trackAndroidModule(appId,
                                                        module.getName(),
                                                        androidModel.isLibrary(),
                                                        androidModel.getAndroidProject().getSigningConfigs().size(),
                                                        androidModel.getBuildTypeNames().size(),
                                                        androidModel.getProductFlavorNames().size(),
                                                        getFlavorDimensions(androidModel).size());
        }
      }
    }
  }

  @NotNull
  private static Collection<String> getFlavorDimensions(@NotNull AndroidGradleModel androidModel) {
    AndroidProject androidProject = androidModel.getAndroidProject();
    try {
      return androidProject.getFlavorDimensions();
    }
    catch (UnsupportedMethodException e) {
      LOG.warn("Invoking 'getFlavorDimensions' on old Gradle model", e);
    }
    return Collections.emptyList();
  }

  private static void trackExternalDependenciesInAndroidApp(@NotNull AndroidGradleModel model) {
    Collection<Variant> variants = model.getAndroidProject().getVariants();
    if (variants.isEmpty()) {
      return;
    }

    Variant chosen = null;
    // We want to track the "release" variants.
    for (Variant variant : variants) {
      if ("release".equals(variant.getBuildType())) {
        chosen = variant;
        break;
      }
    }

    // If we could not find a "release" variant, pick the first one.
    if (chosen == null) {
      chosen = getFirstItem(variants);
    }

    if (chosen != null) {
      trackLibraryCount(chosen, model);
    }
  }

  private static void trackLibraryCount(@NotNull Variant variant, @NotNull AndroidGradleModel model) {
    DependencyFiles files = new DependencyFiles();

    AndroidArtifact artifact = variant.getMainArtifact();
    String applicationId = artifact.getApplicationId();

    Dependencies dependencies = getDependencies(artifact, model.getModelVersion());
    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
      addJarLibraryAndDependencies(javaLibrary, files);
    }

    for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
      addAarLibraryAndDependencies(androidLibrary, files);
    }

    UsageTracker.getInstance().trackLibraryCount(applicationId, files.jars.size(), files.aars.size());
  }

  private static void addJarLibraryAndDependencies(@NotNull JavaLibrary javaLibrary, @NotNull DependencyFiles files) {
    File jarFile = javaLibrary.getJarFile();
    if (files.jars.contains(jarFile)) {
      return;
    }
    files.jars.add(jarFile);
    for (JavaLibrary dependency : javaLibrary.getDependencies()) {
      addJarLibraryAndDependencies(dependency, files);
    }
  }

  private static void addAarLibraryAndDependencies(@NotNull AndroidLibrary androidLibrary, @NotNull DependencyFiles files) {
    String gradlePath = androidLibrary.getProject();
    if (isEmpty(gradlePath)) {
      // This is an external dependency (i.e. no Gradle path).
      File file = androidLibrary.getJarFile();
      if (files.aars.contains(file)) {
        return;
      }

      files.aars.add(file);

      for (AndroidLibrary library : androidLibrary.getLibraryDependencies()) {
        addAarLibraryAndDependencies(library, files);
      }
    }
  }

  private static class DependencyFiles {
    final Set<File> aars = Sets.newHashSet();
    final Set<File> jars = Sets.newHashSet();
  }
}
