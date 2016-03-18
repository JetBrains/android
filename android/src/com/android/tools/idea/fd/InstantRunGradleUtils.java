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
package com.android.tools.idea.fd;

import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class InstantRunGradleUtils {
  /**
   * Returns whether the currently selected variant can be used with Instant Run on a device with the given API level.
   */
  public static BooleanStatus getIrSupportStatus(@NotNull Module module, @NotNull AndroidVersion deviceVersion) {
    if (!InstantRunSettings.isInstantRunEnabled()) {
      return BooleanStatus.failure("not enabled in settings");
    }

    return getIrSupportStatus(getAppModel(module), deviceVersion);
  }

  @NotNull
  public static BooleanStatus getIrSupportStatus(@Nullable AndroidGradleModel model, @Nullable AndroidVersion deviceVersion) {
    if (model == null) {
      return BooleanStatus.failure("no gradle model");
    }

    BooleanStatus status = getIrSupportStatus(model);
    if (!status.success) {
      return status;
    }

    if (deviceVersion == null) {
      return BooleanStatus.SUCCESS;
    }

    Variant variant = model.getSelectedVariant();
    BuildTypeContainer buildTypeContainer = model.findBuildType(model.getSelectedVariant().getBuildType());
    assert buildTypeContainer != null;
    BuildType buildType = buildTypeContainer.getBuildType();
    ProductFlavor mergedFlavor = variant.getMergedFlavor();

    if (isLegacyMultiDex(buildType, mergedFlavor)) {
      // We don't support legacy multi-dex on Dalvik.
      if (deviceVersion.isGreaterOrEqualThan(AndroidVersion.ART_RUNTIME.getApiLevel())) {
        return BooleanStatus.SUCCESS;
      }
      else {
        return BooleanStatus.failure(
          "Instant Run does not support deploying build variants with multidex enabled, to a target with API level 20 or below.<br><br>" +
          "To use Instant Run with a multidex enabled build variant, deploy to a target with API level 21 or higher.");
      }
    }

    return BooleanStatus.SUCCESS;
  }

  @NotNull
  private static BooleanStatus getIrSupportStatus(@NotNull AndroidGradleModel model) {
    String version = model.getAndroidProject().getModelVersion();
    if (!modelSupportsInstantRun(model)) {
      String msg = "Android Plugin for Gradle version " + version + " does not support Instant Run. Please update to version " +
                   InstantRunManager.MINIMUM_GRADLE_PLUGIN_VERSION_STRING;
      return BooleanStatus.failure(msg);
    }

    if (variantSupportsInstantRun(model)) {
      return BooleanStatus.SUCCESS;
    }
    else {
      String msg = "variant '" + model.getSelectedVariant().getName() + "' uses an unsupported feature (e.g. ProGuard)";
      return BooleanStatus.failure(msg);
    }
  }

  // TODO: Move this logic to Variant, so we don't have to duplicate it in AS.
  private static boolean isLegacyMultiDex(@NotNull BuildType buildType, @NotNull ProductFlavor mergedFlavor) {
    if (buildType.getMultiDexEnabled() != null) {
      return buildType.getMultiDexEnabled();
    }
    if (mergedFlavor.getMultiDexEnabled() != null) {
      return mergedFlavor.getMultiDexEnabled();
    }
    return false;
  }

  public static boolean variantSupportsInstantRun(@NotNull AndroidGradleModel model) {
    try {
      return model.getSelectedVariant().getMainArtifact().getInstantRun().isSupportedByArtifact();
    } catch (Throwable e) {
      return false;
    }
  }

  /** Returns true if Instant Run is supported for this gradle model (whether or not it's enabled) */
  public static boolean modelSupportsInstantRun(@NotNull AndroidGradleModel model) {
    GradleVersion modelVersion = model.getModelVersion();
    return modelVersion == null || modelVersion.compareTo(InstantRunManager.MINIMUM_GRADLE_PLUGIN_VERSION) >= 0;
  }

  @NotNull
  public static String getIncrementalDexTask(@NotNull AndroidGradleModel model, @NotNull Module module) {
    assert modelSupportsInstantRun(model) : module;
    String taskName = model.getSelectedVariant().getMainArtifact().getInstantRun().getIncrementalAssembleTaskName();
    String gradlePath = GradleUtil.getGradlePath(module);
    if (gradlePath != null) {
      taskName = gradlePath + ":" + taskName;
    }
    return taskName;
  }

  @Nullable
  public static AndroidGradleModel getAppModel(@NotNull Module module) {
    AndroidFacet facet = findAppModule(module, module.getProject());
    if (facet == null) {
      return null;
    }

    return AndroidGradleModel.get(facet);
  }

  @Nullable
  public static AndroidFacet findAppModule(@Nullable Module module, @NotNull Project project) {
    if (module != null) {
      assert module.getProject() == project;
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }

    // TODO: Here we should really look for app modules that *depend*
    // on the given module (if non null), not just use the first app
    // module we find.

    for (Module m : ModuleManager.getInstance(project).getModules()) {
      AndroidFacet facet = AndroidFacet.getInstance(m);
      if (facet != null && !facet.isLibraryProject()) {
        return facet;
      }
    }
    return null;
  }

  @Nullable
  public static InstantRunBuildInfo getBuildInfo(@NonNull AndroidGradleModel model) {
    File buildInfo = getLocalBuildInfoFile(model);
    if (!buildInfo.exists()) {
      return null;
    }

    String xml;
    try {
      xml = Files.toString(buildInfo, Charsets.UTF_8);
    }
    catch (IOException e) {
      return null;
    }

    return InstantRunBuildInfo.get(xml);
  }

  @NotNull
  private static File getLocalBuildInfoFile(@NotNull AndroidGradleModel model) {
    InstantRun instantRun = model.getSelectedVariant().getMainArtifact().getInstantRun();
    return instantRun.getInfoFile();
  }
}
