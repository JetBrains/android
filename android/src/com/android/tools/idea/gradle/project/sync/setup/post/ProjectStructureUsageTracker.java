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
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.ide.android.IdeAndroidProject;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.google.common.collect.Sets;
import com.google.wireless.android.sdk.stats.*;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.fd.InstantRunSettings.isInstantRunEnabled;
import static com.android.tools.idea.fd.gradle.InstantRunGradleUtils.modelSupportsInstantRun;
import static com.android.tools.idea.fd.gradle.InstantRunGradleUtils.variantSupportsInstantRun;
import static com.android.tools.idea.gradle.plugin.AndroidPluginGeneration.COMPONENT;
import static com.android.tools.idea.stats.AndroidStudioUsageTracker.anonymizeUtf8;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.NDK_COMPILE;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
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
        if (androidModel.getAndroidProject().getProjectType() == PROJECT_TYPE_LIBRARY) {
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

      String appId = anonymizeUtf8(model.getApplicationId());
      AndroidProject androidProject = model.getAndroidProject();
      GradleVersion gradleVersion = GradleVersions.getInstance().getGradleVersion(myProject);
      if (gradleVersion == null) {
        gradleVersion = new GradleVersion(0, 0, 0);
      }

      // @formatter:off
      GradleModule gradleModule = GradleModule.newBuilder().setTotalModuleCount(modules.length)
                                                           .setAppModuleCount(appCount)
                                                           .setLibModuleCount(libCount)
                                                           .build();
      // @formatter:on

      for (Module module : modules) {
        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          IdeAndroidProject moduleAndroidProject = androidModel.getAndroidProject();
          GradleAndroidModule.Builder androidModule = GradleAndroidModule.newBuilder();
          // @formatter:off
          androidModule.setModuleName(anonymizeUtf8(module.getName()))
                       .setSigningConfigCount(moduleAndroidProject.getSigningConfigs().size())
                       .setIsLibrary(moduleAndroidProject.getProjectType() == PROJECT_TYPE_LIBRARY)
                       .setBuildTypeCount(androidModel.getBuildTypeNames().size())
                       .setFlavorCount(androidModel.getProductFlavorNames().size())
                       .setFlavorDimension(moduleAndroidProject.getFlavorDimensions().size());
          // @formatter:on
          gradleAndroidModules.add(androidModule.build());
        }

        boolean shouldReportNative = false;
        NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
        NativeBuildSystemType buildSystemType = UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
        String moduleName = "";

        if (ndkModuleModel != null) {
          shouldReportNative = true;
          if (ndkModuleModel.modelVersionIsAtLeast("2.2.0")) {
            for (String buildSystem : ndkModuleModel.getAndroidProject().getBuildSystems()) {
              buildSystemType = stringToBuildSystemType(buildSystem);
            }
          }
          else {
            buildSystemType = GRADLE_EXPERIMENTAL;
          }
          moduleName = anonymizeUtf8(ndkModuleModel.getModuleName());
        }
        else if (androidModel != null && areNativeLibrariesPresent(androidModel.getAndroidProject())) {
          shouldReportNative = true;

          if (AndroidPluginGeneration.find(module) == COMPONENT) {
            buildSystemType = GRADLE_EXPERIMENTAL;
          }
          else {
            buildSystemType = NDK_COMPILE;
          }
        }
        if (shouldReportNative) {
          GradleNativeAndroidModule.Builder nativeModule = GradleNativeAndroidModule.newBuilder();
          nativeModule.setModuleName(moduleName).setBuildSystemType(buildSystemType);
          gradleNativeAndroidModules.add(nativeModule.build());
        }
      }
      GradleBuildDetails.Builder gradleBuild = GradleBuildDetails.newBuilder();
      // @formatter:off
      gradleBuild.setAppId(appId).setAndroidPluginVersion(androidProject.getModelVersion())
                                 .setGradleVersion(gradleVersion.toString())
                                 .setUserEnabledIr(isInstantRunEnabled())
                                 .setModelSupportsIr(modelSupportsInstantRun(model))
                                 .setVariantSupportsIr(variantSupportsInstantRun(model))
                                 .addAllLibraries(gradleLibraries)
                                 .addModules(gradleModule)
                                 .addAllAndroidModules(gradleAndroidModules)
                                 .addAllNativeAndroidModules(gradleNativeAndroidModules);
      // @formatter:on
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
      event.setCategory(GRADLE).setKind(GRADLE_BUILD_DETAILS).setGradleBuildDetails(gradleBuild);
      UsageTracker.getInstance().log(event);
    }
  }

  @VisibleForTesting
  static NativeBuildSystemType stringToBuildSystemType(@NotNull String buildSystem) {
    switch (buildSystem) {
      case "ndkBuild":
        return NativeBuildSystemType.NDK_BUILD;
      case "cmake":
        return NativeBuildSystemType.CMAKE;
      case "ndkCompile":
        return NDK_COMPILE;
      case "gradle":
        return GRADLE_EXPERIMENTAL;
      default:
        return UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
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
      return trackLibraryCount(chosen);
    }
    return null;
  }

  @NotNull
  private static GradleLibrary trackLibraryCount(@NotNull Variant variant) {
    DependencyFiles files = new DependencyFiles();

    AndroidArtifact artifact = variant.getMainArtifact();

    Dependencies dependencies = artifact.getDependencies();
    for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
      addJarLibraryAndDependencies(javaLibrary, files);
    }

    for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
      addAarLibraryAndDependencies(androidLibrary, files);
    }

    // @formatter:off
    return GradleLibrary.newBuilder().setAarDependencyCount(files.aars.size())
                                     .setJarDependencyCount(files.jars.size())
                                     .build();
    // @formatter:on
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
        if (androidModel.getAndroidProject().getProjectType() == PROJECT_TYPE_APP) {
          return androidModel.getApplicationId();
        }
      }
    }
    return null;
  }
}
