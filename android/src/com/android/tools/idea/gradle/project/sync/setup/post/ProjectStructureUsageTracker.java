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

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.CMAKE;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.NDK_COMPILE;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;

import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.stats.AnonymizerUtil;
import com.android.tools.idea.stats.UsageTrackerUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.GradleAndroidModule;
import com.google.wireless.android.sdk.stats.GradleBuildDetails;
import com.google.wireless.android.sdk.stats.GradleLibrary;
import com.google.wireless.android.sdk.stats.GradleModule;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule;
import com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tracks, using {@link UsageTracker}, the structure of a project.
 */
public class ProjectStructureUsageTracker {
  private static final Logger LOG = Logger.getInstance(ProjectStructureUsageTracker.class);

  @NotNull private final Project myProject;

  public ProjectStructureUsageTracker(@NotNull Project project) {
    myProject = project;
  }

  public void trackProjectStructure() {
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
        if (androidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY) {
          libModel = androidModel;
          libCount++;
          continue;
        }
        appModel = androidModel;
        appCount++;
        GradleLibrary gradleLibrary = trackExternalDependenciesInAndroidApp(androidModel);
        gradleLibraries.add(gradleLibrary);
      }
    }

    // Ideally we would like to get data from an "app" module, but if the project does not have one (which would be unusual, we can use
    // an Android library one.)
    AndroidModuleModel model = appModel != null ? appModel : libModel;
    if (model != null) {
      List<GradleAndroidModule> gradleAndroidModules = new ArrayList<>();
      List<GradleNativeAndroidModule> gradleNativeAndroidModules = new ArrayList<>();

      String appId = AnonymizerUtil.anonymizeUtf8(model.getApplicationId());
      IdeAndroidProject androidProject = model.getAndroidProject();
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
          androidModule.setModuleName(AnonymizerUtil.anonymizeUtf8(module.getName()))
                       .setSigningConfigCount(moduleAndroidProject.getSigningConfigs().size())
                       .setIsLibrary(moduleAndroidProject.getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)
                       .setBuildTypeCount(androidModel.getBuildTypeNames().size())
                       .setFlavorCount(androidModel.getProductFlavorNames().size())
                       .setFlavorDimension(moduleAndroidProject.getFlavorDimensions().size());
          // @formatter:on
          gradleAndroidModules.add(androidModule.build());
        }

        boolean shouldReportNative = false;
        NdkModuleModel ndkModel = NdkModuleModel.get(module);
        NativeBuildSystemType buildSystemType = UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
        String moduleName = "";

        if (ndkModel != null) {
          shouldReportNative = true;
          if (ndkModel.getFeatures().isBuildSystemNameSupported()) {
            for (String buildSystem : ndkModel.getBuildSystems()) {
              buildSystemType = stringToBuildSystemType(buildSystem);
            }
          }
          else {
            buildSystemType = GRADLE_EXPERIMENTAL;
          }
          moduleName = AnonymizerUtil.anonymizeUtf8(ndkModel.getModuleName());
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
                                 .addAllLibraries(gradleLibraries)
                                 .addModules(gradleModule)
                                 .addAllAndroidModules(gradleAndroidModules)
                                 .addAllNativeAndroidModules(gradleNativeAndroidModules);
      // @formatter:on
      AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
      event.setCategory(GRADLE).setKind(GRADLE_BUILD_DETAILS).setGradleBuildDetails(gradleBuild);
      UsageTracker.log(UsageTrackerUtils.withProjectId(event, myProject));
    }
  }

  @VisibleForTesting
  static NativeBuildSystemType stringToBuildSystemType(@NotNull String buildSystem) {
    switch (buildSystem) {
      case "ndkBuild":
        return NDK_BUILD;
      case "cmake":
        return CMAKE;
      case "ndkCompile":
        return NDK_COMPILE;
      case "gradle":
        return GRADLE_EXPERIMENTAL;
      default:
        return UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;
    }
  }

  private static GradleLibrary trackExternalDependenciesInAndroidApp(@NotNull AndroidModuleModel model) {
    // Use Ref because lambda function argument to forEachVariant only works with final variables.
    Ref<IdeVariant> chosenVariant = new Ref<>();
    // We want to track the "release" variants.
    model.getVariants().forEach(variant -> {
      if ("release".equals(variant.getBuildType())) {
        chosenVariant.set(variant);
      }
    });

    // If we could not find a "release" variant, pick the selected one.
    if (chosenVariant.get() == null) {
      chosenVariant.set(model.getSelectedVariant());
    }

    IdeDependencies dependencies = chosenVariant.get().getMainArtifact().getLevel2Dependencies();
    // @formatter:off
    return GradleLibrary.newBuilder().setAarDependencyCount(dependencies.getAndroidLibraries().size())
                                     .setJarDependencyCount(dependencies.getJavaLibraries().size())
                                     .build();
    // @formatter:on
  }
}
