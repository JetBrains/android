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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Tracks, using {@link UsageTracker}, the structure of a project.
 */
class ExternalDependenciesUsageTracker {
  @NotNull private final Project myProject;

  ExternalDependenciesUsageTracker(@NotNull Project project) {
    myProject = project;
  }

  void trackExternalDependenciesInAndroidApps() {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ModuleManager moduleManager = ModuleManager.getInstance(myProject);
        trackExternalDependenciesInAndroidApps(moduleManager.getModules());
      }
    });
  }

  private static void trackExternalDependenciesInAndroidApps(@NotNull Module[] modules) {
    for (Module module : modules) {
      AndroidGradleModel androidModel = AndroidGradleModel.get(module);
      if (androidModel != null) {
        AndroidProject androidProject = androidModel.getAndroidProject();
        if (!androidProject.isLibrary()) {
          trackExternalDependenciesInAndroidApp(androidProject);
        }
      }
    }
  }

  private static void trackExternalDependenciesInAndroidApp(@NotNull AndroidProject model) {
    Collection<Variant> variants = model.getVariants();
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
      trackLibraryCount(chosen);
    }
  }

  private static void trackLibraryCount(@NotNull Variant variant) {
    DependencyFiles files = new DependencyFiles();

    AndroidArtifact artifact = variant.getMainArtifact();
    String applicationId = artifact.getApplicationId();

    String id = Hashing.sha256().hashString(applicationId, Charsets.UTF_8).toString();
    Dependencies dependencies = artifact.getDependencies();
    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
      addJarLibraryAndDependencies(javaLibrary, files);
    }

    for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
      addAarLibraryAndDependencies(androidLibrary, files);
    }

    UsageTracker.getInstance().trackLibraryCount(id, files.jars.size(), files.aars.size());
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
