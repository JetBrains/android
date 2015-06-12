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
package org.jetbrains.android.run;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.model.ManifestInfo;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.ActivityAlias;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * An Android application launcher that determines the default launch activity and launches that activity.
 */
public class DefaultActivityLauncher extends AndroidActivityLauncher {

  @NotNull
  private final AndroidFacet myFacet;

  public DefaultActivityLauncher(@NotNull AndroidFacet facet) {
    myFacet = facet;
  }

  @NotNull
  @Override
  protected String getActivityName() throws ActivityNameException {
    String activityName = computeDefaultActivity(myFacet);
    if (activityName == null) {
      throw new ActivityNameException(AndroidBundle.message("default.activity.not.found.error"));
    }
    return activityName;
  }

  @Nullable
  @VisibleForTesting
  static String computeDefaultActivity(@NotNull final AndroidFacet facet) throws ActivityNameException {
    if (!facet.getProperties().USE_CUSTOM_COMPILER_MANIFEST) {
      final boolean useMergedManifest = facet.isGradleProject() || facet.getProperties().ENABLE_MANIFEST_MERGING;
      final ManifestInfo manifestInfo = ManifestInfo.get(facet.getModule(), useMergedManifest);

      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return AndroidUtils.getDefaultLauncherActivityName(manifestInfo.getActivities(), manifestInfo.getActivityAliases());
        }
      });
    }

    File manifestCopy = null;
    try {
      Pair<File, String> pair;
      try {
        pair = AndroidRunConfigurationBase.getCopyOfCompilerManifestFile(facet);
      } catch (IOException e) {
        throw new ActivityNameException("I/O error getting manifest: " + e.getMessage(), e);
      }
      manifestCopy = pair != null ? pair.getFirst() : null;
      VirtualFile manifestVFile = manifestCopy != null ? LocalFileSystem.getInstance().findFileByIoFile(manifestCopy) : null;
      final Manifest manifest =
        manifestVFile == null ? null : AndroidUtils.loadDomElement(facet.getModule(), manifestVFile, Manifest.class);
      if (manifest == null) {
        throw new ActivityNameException("Cannot find " + SdkConstants.FN_ANDROID_MANIFEST_XML + " file");
      }
      return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          return AndroidUtils.getDefaultLauncherActivityName(manifest);
        }
      });
    }
    finally {
      if (manifestCopy != null) {
        FileUtil.delete(manifestCopy.getParentFile());
      }
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (AndroidRunConfiguration.doesPackageContainMavenProperty(myFacet)) {
      return;
    }
    Module module = myFacet.getModule();

    List<Activity> activities = ManifestInfo.get(module, true).getActivities();
    List<ActivityAlias> activityAliases = ManifestInfo.get(module, true).getActivityAliases();
    String activity = AndroidUtils.getDefaultLauncherActivityName(activities, activityAliases);
    if (activity != null) {
      return;
    }
    throw new RuntimeConfigurationError(AndroidBundle.message("default.activity.not.found.error"));
  }
}
