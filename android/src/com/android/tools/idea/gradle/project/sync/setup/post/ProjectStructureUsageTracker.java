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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.annotations.VisibleForTesting;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.stats.AndroidStudioUsageTracker;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.*;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.COMPONENT;
import static com.android.tools.idea.gradle.util.GradleUtil.getDependencies;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * Tracks, using {@link UsageTracker}, the structure of a project.
 */
public class ProjectStructureUsageTracker {
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

  @VisibleForTesting
  void trackProjectStructure(@NotNull Module[] modules) {
    AndroidModuleModel appModel = null;
    AndroidModuleModel libModel = null;

    int appCount = 0;
    int libCount = 0;

    List<GradleLibrary> gradleLibraries = new ArrayList<>();

    for (Module module : modules) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        if (androidModel.getProjectType() == PROJECT_TYPE_LIBRARY) {
          libModel = androidModel;
          libCount++;
          continue;
        }
        appModel = androidModel;
        appCount++;
        GradleLibrary gradleLibrary = trackExternalDependenciesInAndroidApp(androidModel);
        if (gradleLibrary != null) {
          gradleLibraries.add(gradleLibrary);
        }
      }
    }

    // Ideally we would like to get data from an "app" module, but if the project does not have one (which would be unusual, we can use
    // an Android library one.)
    AndroidModuleModel model = appModel != null ? appModel : libModel;
    if (model != null) {
      List<GradleAndroidModule> gradleAndroidModules = new ArrayList<>();
      List<GradleNativeAndroidModule> gradleNativeAndroidModules = new ArrayList<>();

      String appId = AndroidStudioUsageTracker.anonymizeUtf8(model.getApplicationId());
      AndroidProject androidProject = model.getAndroidProject();
      GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(myProject);
      if (gradleVersion == null) {
        gradleVersion = new GradleVersion(0, 0, 0);
      }

      GradleModule gradleModule = GradleModule.newBuilder()
        .setTotalModuleCount(modules.length)
        .setAppModuleCount(appCount)
        .setLibModuleCount(libCount)
        .build();

      for (Module module : modules) {
        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          gradleAndroidModules.add(GradleAndroidModule.newBuilder()
                                  .setModuleName(AndroidStudioUsageTracker.anonymizeUtf8(module.getName()))
                                  .setSigningConfigCount(androidModel.getAndroidProject().getSigningConfigs().size())
                                  .setIsLibrary(androidModel.getProjectType() == PROJECT_TYPE_LIBRARY)
                                  .setBuildTypeCount(androidModel.getBuildTypeNames().size())
                                  .setFlavorCount(androidModel.getProductFlavorNames().size())
                                  .setFlavorDimension(getFlavorDimensions(androidModel).size())
                                  .build());
        }

        boolean shouldReportNative = false;
        NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
        NativeBuildSystemType buildSystemType = NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
        String moduleName = "";

        if (ndkModuleModel != null) {
          shouldReportNative = true;
          if (ndkModuleModel.modelVersionIsAtLeast("2.2.0")) {
            for (String buildSystem : ndkModuleModel.getAndroidProject().getBuildSystems()) {
              buildSystemType = stringToBuildSystemType(buildSystem);
            }
          }
          else {
            buildSystemType = NativeBuildSystemType.GRADLE_EXPERIMENTAL;
          }
          moduleName = AndroidStudioUsageTracker.anonymizeUtf8(ndkModuleModel.getModuleName());
        }
        else if (androidModel != null && areNativeLibrariesPresent(androidModel.getAndroidProject())) {
          shouldReportNative = true;

          if (AndroidPluginGeneration.find(module) == COMPONENT) {
            buildSystemType = NativeBuildSystemType.GRADLE_EXPERIMENTAL;
          }
          else {
            buildSystemType = NativeBuildSystemType.NDK_COMPILE;
          }
        }
        if (shouldReportNative) {
          gradleNativeAndroidModules.add(GradleNativeAndroidModule.newBuilder()
                                           .setModuleName(moduleName)
                                           .setBuildSystemType(buildSystemType)
                                           .build());
        }
      }
      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(EventCategory.GRADLE)
                                       .setKind(AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS)
                                       .setGradleBuildDetails(GradleBuildDetails.newBuilder()
                                                                .setAppId(appId)
                                                                .setAndroidPluginVersion(androidProject.getModelVersion())
                                                                .setGradleVersion(gradleVersion.toString())
                                                                .setUserEnabledIr(InstantRunSettings.isInstantRunEnabled())
                                                                .setModelSupportsIr(InstantRunGradleUtils.modelSupportsInstantRun(model))
                                                                .setVariantSupportsIr(
                                                                  InstantRunGradleUtils.variantSupportsInstantRun(model))
                                                                .addAllLibraries(gradleLibraries)
                                                                .addModules(gradleModule)
                                                                .addAllAndroidModules(gradleAndroidModules)
                                                                .addAllNativeAndroidModules(gradleNativeAndroidModules)));
    }
  }

  @VisibleForTesting static NativeBuildSystemType stringToBuildSystemType(@NotNull String buildSystem) {
    switch (buildSystem) {
      case "ndk-build":
        return NativeBuildSystemType.NDK_BUILD;
      case "cmake":
        return NativeBuildSystemType.CMAKE;
      case "ndkCompile":
        return NativeBuildSystemType.NDK_COMPILE;
      case "gradle":
        return NativeBuildSystemType.GRADLE_EXPERIMENTAL;
      default:
        return NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
    }
  }

  private static boolean areNativeLibrariesPresent(@NotNull AndroidProject androidProject) {
    String modelVersion = androidProject.getModelVersion();
    // getApiVersion doesn't work prior to 1.2, and API level must be at least 3
    if (modelVersion.startsWith("1.0") || modelVersion.startsWith("1.1") || androidProject.getApiVersion() < 3) {
      return false;
    }
    for (Variant variant : androidProject.getVariants()) {
      Collection<NativeLibrary> nativeLibraries = variant.getMainArtifact().getNativeLibraries();
      if (nativeLibraries != null && !nativeLibraries.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static Collection<String> getFlavorDimensions(@NotNull AndroidModuleModel androidModel) {
    AndroidProject androidProject = androidModel.getAndroidProject();
    try {
      return androidProject.getFlavorDimensions();
    }
    catch (UnsupportedMethodException e) {
      LOG.warn("Invoking 'getFlavorDimensions' on old Gradle model", e);
    }
    return Collections.emptyList();
  }

  private static GradleLibrary trackExternalDependenciesInAndroidApp(@NotNull AndroidModuleModel model) {
    Collection<Variant> variants = model.getAndroidProject().getVariants();
    if (variants.isEmpty()) {
      return null;
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
      return trackLibraryCount(chosen, model);
    }
    return null;
  }

  private static GradleLibrary trackLibraryCount(@NotNull Variant variant, @NotNull AndroidModuleModel model) {
    DependencyFiles files = new DependencyFiles();

    AndroidArtifact artifact = variant.getMainArtifact();

    Dependencies dependencies = getDependencies(artifact, model.getModelVersion());
    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
      addJarLibraryAndDependencies(javaLibrary, files);
    }

    for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
      addAarLibraryAndDependencies(androidLibrary, files);
    }

    return GradleLibrary.newBuilder()
      .setAarDependencyCount(files.aars.size())
      .setJarDependencyCount(files.jars.size())
      .build();
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

  @Nullable
  public static String getApplicationId(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel != null) {
        if (androidModel.getProjectType() == PROJECT_TYPE_APP) {
          return androidModel.getApplicationId();
        }
      }
    }
    return null;
  }
}
