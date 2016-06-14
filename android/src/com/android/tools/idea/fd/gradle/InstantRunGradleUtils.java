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
package com.android.tools.idea.fd.gradle;

import com.android.annotations.NonNull;
import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.AndroidVersion;
import com.android.tools.fd.client.InstantRunBuildInfo;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.run.GradleInstantRunContext;
import com.android.tools.idea.run.ApkProviderUtil;
import com.android.tools.idea.run.ApkProvisionException;
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
  @NotNull
  public static InstantRunGradleSupport getIrSupportStatus(@Nullable AndroidGradleModel model, @Nullable AndroidVersion deviceVersion) {
    if (model == null) {
      return InstantRunGradleSupport.NO_GRADLE_MODEL;
    }

    if (!modelSupportsInstantRun(model)) {
      return InstantRunGradleSupport.GRADLE_PLUGIN_TOO_OLD;
    }

    if (!variantSupportsInstantRun(model)) {
      return InstantRunGradleSupport.VARIANT_DOES_NOT_SUPPORT_INSTANT_RUN;
    }

    if (deviceVersion == null) {
      return InstantRunGradleSupport.SUPPORTED;
    }

    Variant variant = model.getSelectedVariant();
    BuildTypeContainer buildTypeContainer = model.findBuildType(model.getSelectedVariant().getBuildType());
    assert buildTypeContainer != null;
    BuildType buildType = buildTypeContainer.getBuildType();
    ProductFlavor mergedFlavor = variant.getMergedFlavor();

    if (isLegacyMultiDex(buildType, mergedFlavor)) {
      // We don't support legacy multi-dex on Dalvik.
      if (!deviceVersion.isGreaterOrEqualThan(AndroidVersion.ART_RUNTIME.getApiLevel())) {
        return InstantRunGradleSupport.LEGACY_MULTIDEX_REQUIRES_ART;
      }
    }

    return InstantRunGradleSupport.SUPPORTED;
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

  @Nullable
  public static InstantRunContext createGradleProjectContext(@NotNull Module module) {
    AndroidFacet appFacet = findAppModule(module, module.getProject());
    if (appFacet == null) {
      return null;
    }

    return createGradleProjectContext(appFacet);
  }

  @Nullable
  public static InstantRunContext createGradleProjectContext(@NotNull AndroidFacet facet) {
    try {
      String pkgName = ApkProviderUtil.computePackageName(facet);
      return new GradleInstantRunContext(pkgName, facet);
    }
    catch (ApkProvisionException e) {
      return null;
    }
  }
}
