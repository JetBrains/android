/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.gradle.run.PostBuildModelProvider;
import com.android.tools.idea.run.AndroidAppRunConfigurationBase;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.utils.HtmlBuilder;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.exportSignedPackage.ChooseBundleOrApkStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Various utility methods to navigate through various parts (dynamic features, base split, etc.)
 * of dynamic apps.
 */
public class DynamicAppUtils {
  /** Index for user clicking on the confirm button of the dialog. **/
  private static final int UPDATE_BUTTON_INDEX = 1;

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module.
   */
  @NotNull
  public static List<Module> getDependentFeatureModules(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return ImmutableList.of();
    }
    return getDependentFeatureModules(module.getProject(), androidModule.getAndroidProject());
  }

  /**
   * Returns the Base Module of the specified dynamic feature {@link Module module}, or null if none is found.
   */
  @Nullable
  public static Module getBaseFeature(@NotNull Module module) {
    String gradlePath = getGradlePath(module);
    if (gradlePath == null) {
      return null;
    }

    return Arrays.stream(ModuleManager.getInstance(module.getProject()).getModules())
      .filter(baseModule -> {
        AndroidModuleModel baseModel = AndroidModuleModel.get(baseModule);
        return baseModel != null && baseModel.getAndroidProject().getDynamicFeatures().contains(gradlePath);
      })
      .findFirst()
      .orElse(null);
  }
  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module.
   */
  @NotNull
  public static List<Module> getDependentFeatureModules(@NotNull Project project, @NotNull IdeAndroidProject androidProject) {
    Map<String, Module> featureMap = getDynamicFeaturesMap(project);
    return androidProject.getDynamicFeatures().stream()
      .map(featurePath -> featureMap.get(featurePath))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  /**
   * Returns the list of {@link Module modules} to build for a given base module.
   */
  @NotNull
  public static List<Module> getModulesToBuild(@NotNull Module module) {
    return Stream
      .concat(Stream.of(module), getDependentFeatureModules(module).stream())
      .collect(Collectors.toList());
  }

  /**
   * Returns {@code true} if the base module is instant enabled
   */
  @NotNull
  public static boolean baseIsInstantEnabled(@NotNull Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      AndroidModuleModel model = AndroidModuleModel.get(module);
      if (model != null && model.getAndroidProject().isBaseSplit()) {
        if (model.getSelectedVariant().isInstantAppCompatible()) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static List<Module> getModulesSupportingBundleTask(@NotNull Project project) {
    return ProjectStructure.getInstance(project).getAppModules().stream()
      .filter(module -> supportsBundleTask(module))
      .collect(Collectors.toList());
  }

  /**
   * Returns {@code true} if the module supports the "bundle" task, i.e. if the Gradle
   * plugin associated to the module is of high enough version number and supports
   * the "Bundle" tool.
   */
  public static boolean supportsBundleTask(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return false;
    }
    return !StringUtil.isEmpty(androidModule.getSelectedVariant().getMainArtifact().getBundleTaskName());
  }

  /**
   * Displays a message prompting user to update their project's gradle in order to use Android App Bundles.
   * @return {@code true} if user agrees to update, {@code false} if user declines.
   */
  public static boolean promptUserForGradleUpdate(@NotNull Project project) {
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();
    builder.add("Building Android App Bundles requires you to update to the latest version of the Android Gradle Plugin.");
    builder.newline();
    builder.addLink("Learn More", ChooseBundleOrApkStep.DOC_URL);
    builder.newline();
    builder.newline();
    builder.add("App bundles allow you to support multiple device configurations from a single build artifact.");
    builder.newline();
    builder.add("App stores that support the bundle format use it to build and sign your APKs for you, and");
    builder.newline();
    builder.add("serve those APKs to users as needed.");
    builder.newline();
    builder.newline();
    builder.closeHtmlBody();
    int result = Messages.showDialog(project,
                                     builder.getHtml(),
                                     "Update the Android Gradle Plugin",
                                     new String[]{Messages.CANCEL_BUTTON, "Update"},
                                     UPDATE_BUTTON_INDEX /* Default button */,
                                     AllIcons.General.WarningDialog);

    if (result == UPDATE_BUTTON_INDEX) {
      ApplicationManager.getApplication().invokeLater(() -> {
        GradleVersion gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
        GradleVersion pluginVersion = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get());
        AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
        updater.updatePluginVersion(pluginVersion, gradleVersion);
      });
    }
    return result == UPDATE_BUTTON_INDEX;
  }

  /**
   * TODO: Until the "selectApks" tasks lets us specify the list of features, we rely on the fact
   * the file names created by the bundle tool are of the form "featureName-xxx.apk" where
   * "xxx" is a single word name used by the bundle tool (e.g. "mdpi", "master")
   */
  @NotNull
  public static String getFeatureNameFromPathHack(@NotNull Path path) {
    String fileName = path.getFileName().toString();
    int separatorIndex = fileName.lastIndexOf('-');
    if (separatorIndex < 0) {
      return "";
    }

    return fileName.substring(0, separatorIndex);
  }

  /**
   * Returns an {@link ApkInfo} instance containing all the APKS generated by the "select apks from bundle" Gradle task, or {@code null}
   * in case of unexpected error.
   */
  @Nullable
  public static ApkInfo collectAppBundleOutput(@NotNull Module module,
                                               @NotNull PostBuildModelProvider outputModelProvider,
                                               @NotNull String pkgName) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    if (androidModel == null) {
      getLogger().warn("Android model is null. Sync might have failed");
      return null;
    }

    PostBuildModel model = outputModelProvider.getPostBuildModel();
    if (model == null) {
      getLogger().warn("Post build model is null. Build might have failed.");
      return null;
    }
    AppBundleProjectBuildOutput output = model.findAppBundleProjectBuildOutput(module);
    if (output == null) {
      getLogger().warn("Project output is null. Build may have failed.");
      return null;
    }

    for (AppBundleVariantBuildOutput variantBuildOutput : output.getAppBundleVariantsBuildOutput()) {
      if (variantBuildOutput.getName().equals(androidModel.getSelectedVariant().getName())) {
        File apkFolder = variantBuildOutput.getApkFolder();

        // List all files in the folder
        try (Stream<Path> stream = Files.list(apkFolder.toPath())) {
          List<ApkFileUnit> apks = stream
            .map(path -> new ApkFileUnit(getFeatureNameFromPathHack(path), path.toFile()))
            .collect(Collectors.toList());
          return new ApkInfo(apks, pkgName);
        }
        catch (IOException e) {
          getLogger().warn(String.format("Error reading list of APK files from bundle build output directory \"%s\".", apkFolder), e);
          return null;
        }
      }
    }

    getLogger().warn("Bundle variant build output model has no entries. Build may have failed.");
    return null;
  }

  /**
   * Returns {@code true} if a module should be built using the "select apks from bundle" task
   */
  public static boolean useSelectApksFromBundleBuilder(@NotNull Module module,
                                                       @NotNull RunConfiguration configuration,
                                                       @NotNull List<AndroidDevice> targetDevices) {
    if (configuration instanceof AndroidAppRunConfigurationBase) {
      AndroidAppRunConfigurationBase androidConfiguration = (AndroidAppRunConfigurationBase)configuration;
      if (androidConfiguration.DEPLOY_APK_FROM_BUNDLE) {
        Preconditions.checkArgument(androidConfiguration.DEPLOY);
        return true;
      }
    }

    // If any device is pre-L *and* module has a dynamic feature, we need to use the bundle tool
    if (targetDevices.stream().anyMatch(device -> device.getVersion().getFeatureLevel() < AndroidVersion.VersionCodes.LOLLIPOP) &&
        !getDependentFeatureModules(module).isEmpty()) {
      return true;
    }

    return false;
  }

  /**
   * Returns {@code true} if we should collect the list of languages of the target devices
   * when deploying an app.
   */
  public static boolean shouldCollectListOfLanguages(@NotNull Module module,
                                                     @NotNull RunConfiguration configuration,
                                                     @NotNull List<AndroidDevice> targetDevices) {
    // Don't collect if not using the bundle tool
    if (!useSelectApksFromBundleBuilder(module, configuration, targetDevices)) {
      return false;
    }

    // Only collect if all devices are L or later devices, because pre-L devices don't support split apks, meaning
    // they don't support install on demand, meaning all languages should be installed.
    return targetDevices.stream().allMatch(device -> device.getVersion().getFeatureLevel() >= AndroidVersion.VersionCodes.LOLLIPOP);
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module and are instant app compatible.
   */
  @NotNull
  public static List<Module> getDependentInstantFeatureModules(@NotNull Module module) {
    AndroidModuleModel androidModule = AndroidModuleModel.get(module);
    if (androidModule == null) {
      return ImmutableList.of();
    }
    return getDependentInstantFeatureModules(module.getProject(), androidModule.getAndroidProject());
  }

  /**
   * Returns the list of dynamic feature {@link Module modules} that depend on this base module and are instant app compatible.
   */
  @NotNull
  public static List<Module> getDependentInstantFeatureModules(@NotNull Project project, @NotNull IdeAndroidProject androidProject) {
    Map<String, Module> featureMap = getDynamicFeaturesMap(project);
    return androidProject.getDynamicFeatures().stream()
                         .map(featurePath -> featureMap.get(featurePath))
                         .filter(Objects::nonNull)
                         .filter(f -> AndroidModuleModel.get(f).getSelectedVariant().isInstantAppCompatible())
                         .collect(Collectors.toList());
  }

  public static boolean isFeatureEnabled(@NotNull List<String> myDisabledFeatures, @NotNull ApkFileUnit apkFileUnit) {
    return myDisabledFeatures.stream().noneMatch(m -> featureNameEquals(apkFileUnit, m));
  }

  public static boolean featureNameEquals(@NotNull ApkFileUnit apkFileUnit, @NotNull String featureName) {
    return StringUtil.equals(featureName.replace('-', '_'), apkFileUnit.getModuleName());
  }

  @NotNull
  private static Map<String, Module> getDynamicFeaturesMap(@NotNull Project project) {
    return Arrays.stream(ModuleManager.getInstance(project).getModules())
      .map(module -> {
        // Check the module is a "dynamic feature"
        AndroidModuleModel model = AndroidModuleModel.get(module);
        if (model == null) {
          return null;
        }
        if (model.getAndroidProject().getProjectType() != AndroidProject.PROJECT_TYPE_DYNAMIC_FEATURE) {
          return null;
        }
        String gradlePath = getGradlePath(module);
        if (gradlePath == null) {
          return null;
        }
        return Pair.create(gradlePath, module);
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(p -> p.first, p -> p.second, DynamicAppUtils::handleModuleAmbiguity));
  }

  /**
   * Find the gradle path of the module
   * @return The path of the specified module, or null if it can't retrieve it.
   */
  @Nullable
  private static String getGradlePath(@NotNull Module module) {
    GradleFacet facet = GradleFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    GradleModuleModel gradleModel = facet.getGradleModuleModel();
    if (gradleModel == null) {
      return null;
    }
    return gradleModel.getGradlePath();
  }

  @NotNull
  private static Module handleModuleAmbiguity(@NotNull Module m1, @NotNull Module m2) {
    getLogger().warn(String.format("Unexpected ambiguity processing modules: %s - %s", m1.getName(), m2.getName()));
    return m1;
  }

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(DynamicAppUtils.class);
  }
}
