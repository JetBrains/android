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

import static com.android.tools.idea.model.AndroidManifestIndexQueryUtils.queryUsedFeaturesFromManifestIndex;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_BUILD_DETAILS;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.CMAKE;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.GRADLE_EXPERIMENTAL;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.NDK_BUILD;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.NDK_COMPILE;
import static com.google.wireless.android.sdk.stats.GradleNativeAndroidModule.NativeBuildSystemType.UNKNOWN_NATIVE_BUILD_SYSTEM_TYPE;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncListenerWithRoot;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.model.UsedFeatureRawText;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.dom.manifest.UsesFeature;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;

/**
 * Tracks, using {@link UsageTracker}, the structure of a project.
 */
public class ProjectStructureUsageTracker implements GradleSyncListenerWithRoot {
  private static final Logger LOG = Logger.getInstance(ProjectStructureUsageTracker.class);

  @NotNull private final Project myProject;

  @Override
  public void syncSucceeded(@NotNull Project project, @NotNull @SystemIndependent String rootProjectPath) {
    trackProjectStructure(rootProjectPath);
  }

  public ProjectStructureUsageTracker(@NotNull Project project) {
    myProject = project;
  }

  private void trackProjectStructure(@NotNull String linkedGradleBuildPath) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Run synchronously in unit tests as it is difficult to wait for a pooled thread in unit tests.
      doTrackProjectStructure(linkedGradleBuildPath);
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        doTrackProjectStructure(linkedGradleBuildPath);
      }
      catch (Throwable e) {
        // Any errors in project tracking should not be displayed to the user.
        LOG.warn("Failed to track project structure", e);
      }
    });
  }

  private void doTrackProjectStructure(@NotNull String linkedGradleBuildPath) {
    ExternalProject externalProject = ExternalProjectDataCache.getInstance(myProject).getRootExternalProject(linkedGradleBuildPath);
    if (externalProject != null) {
      trackProjectStructure(externalProject);
    }
  }

  private void trackProjectStructure(@NotNull ExternalProject externalProject) {
    GradleAndroidModel appModel = null;
    GradleAndroidModel libModel = null;

    int appCount = 0;
    int libCount = 0;

    List<GradleLibrary> gradleLibraries = new ArrayList<>();

    for (AndroidFacet facet : ProjectSystemUtil.getAndroidFacets(myProject)) {
      GradleAndroidModel androidModel = GradleAndroidModel.get(facet);
      if (androidModel != null) {
        switch (androidModel.getAndroidProject().getProjectType()) {
          case PROJECT_TYPE_LIBRARY:
            libModel = androidModel;
            libCount++;
            break;
          case PROJECT_TYPE_APP:
            appModel = androidModel;
            appCount++;
            GradleLibrary gradleLibrary = trackExternalDependenciesInAndroidApp(androidModel);
            gradleLibraries.add(gradleLibrary);
            break;
          case PROJECT_TYPE_ATOM:
          case PROJECT_TYPE_DYNAMIC_FEATURE:
          case PROJECT_TYPE_FEATURE:
          case PROJECT_TYPE_INSTANTAPP:
          case PROJECT_TYPE_TEST:
        }
      }
    }

    // Ideally we would like to get data from an "app" module, but if the project does not have one (which would be unusual, we can use
    // an Android library one.)
    GradleAndroidModel model = appModel != null ? appModel : libModel;
    if (model != null) {
      List<GradleAndroidModule> gradleAndroidModules = new ArrayList<>();
      List<GradleNativeAndroidModule> gradleNativeAndroidModules = new ArrayList<>();

      String appId = AnonymizerUtil.anonymizeUtf8(model.getApplicationId());
      IdeAndroidProject androidProject = model.getAndroidProject();
      String gradleVersionString = GradleVersions.getInstance().getGradleVersion(myProject).getVersion();
      if (gradleVersionString == null) {
        gradleVersionString = "0.0.0";
      }

      // @formatter:off
      GradleModule gradleModule = GradleModule.newBuilder().setTotalModuleCount(countGradleProjects(externalProject))
                                                           .setAppModuleCount(appCount)
                                                           .setLibModuleCount(libCount)
                                                           .build();
      // @formatter:on

      for (AndroidFacet facet : ProjectSystemUtil.getAndroidFacets(myProject)) {
        GradleAndroidModel androidModel = GradleAndroidModel.get(facet);
        if (androidModel != null) {
          IdeAndroidProject moduleAndroidProject = androidModel.getAndroidProject();
          GradleAndroidModule.Builder androidModule = GradleAndroidModule.newBuilder();
          // @formatter:off
          androidModule.setModuleName(AnonymizerUtil.anonymizeUtf8(facet.getHolderModule().getName()))
                       .setSigningConfigCount(moduleAndroidProject.getSigningConfigs().size())
                       .setIsLibrary(moduleAndroidProject.getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)
                       .setBuildTypeCount(androidModel.getBuildTypeNames().size())
                       .setFlavorCount(androidModel.getProductFlavorNames().size())
                       .setFlavorDimension(moduleAndroidProject.getFlavorDimensions().size());
          if (!androidModule.getIsLibrary() && isWatchHardwareRequired(facet)) {  // Ignore library modules to query Manifest Index less.
            androidModule.setRequiredHardware(UsesFeature.HARDWARE_TYPE_WATCH);
          }
          // @formatter:on
          gradleAndroidModules.add(androidModule.build());
        }

        boolean shouldReportNative = false;
        NdkModuleModel ndkModel = NdkModuleModel.get(facet.getHolderModule());
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
      gradleBuild.setAppId(appId).setAndroidPluginVersion(androidProject.getAgpVersion())
                                 .setGradleVersion(gradleVersionString)
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

  private boolean isWatchHardwareRequired(AndroidFacet facet) {
    try {
      return DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
        Set<UsedFeatureRawText> usedFeatures = queryUsedFeaturesFromManifestIndex(facet);
        return usedFeatures.contains(new UsedFeatureRawText(UsesFeature.HARDWARE_TYPE_WATCH, null))
               || usedFeatures.contains(new UsedFeatureRawText(UsesFeature.HARDWARE_TYPE_WATCH, "true"));
      });
    } catch (Throwable e) {
      LOG.warn("Manifest Index could not be queried", e);
    }
    return false;
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

  private static GradleLibrary trackExternalDependenciesInAndroidApp(@NotNull GradleAndroidModel model) {
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

    IdeDependencies dependencies = chosenVariant.get().getMainArtifact().getCompileClasspath();
    // @formatter:off
    return GradleLibrary.newBuilder().setAarDependencyCount(dependencies.getAndroidLibraries().size())
                                     .setJarDependencyCount(dependencies.getJavaLibraries().size())
                                     .build();
    // @formatter:on
  }

  private static int countGradleProjects(@NotNull ExternalProject externalProject) {
    List<ExternalProject> projects = new ArrayList<>();
    projects.add(externalProject);
    int count = 0;
    while (!projects.isEmpty()) {
      count++;
      ExternalProject project = projects.remove(0);
      projects.addAll(project.getChildProjects().values());
    }

    return count;
  }
}
