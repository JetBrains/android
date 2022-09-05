/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import com.android.SdkConstants;
import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ApkProvider;
import com.android.tools.idea.run.activity.manifest.NodeActivity;
import com.android.tools.manifest.parser.ManifestInfo;
import com.android.tools.manifest.parser.components.IntentFilter;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Implementation of {@link ActivityLocator} that provides the default activity class name by reading the
 * contents of the manifest embedded in APK(s) from an {@link ApkProvider}.
 */
public class DefaultApkActivityLocator extends ActivityLocator {

  private static final Logger LOG = Logger.getInstance(DefaultApkActivityLocator.class);

  private final ApkProvider myApkProvider;
  private final String myApplicationId;

  public DefaultApkActivityLocator(@NotNull ApkProvider apkProvider, @NotNull String applicationId) {
    myApkProvider = apkProvider;
    myApplicationId = applicationId;
  }

  // This is called before build runs, therefore we cannot validate.
  @Override
  public void validate() throws ActivityLocatorException {
  }

  @NotNull
  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException {
    Collection<ApkInfo> apks;
    try {
      apks = myApkProvider.getApks(device);
    }
    catch (Exception e) {
      throw new ActivityLocatorException("Unable to list apks", e);
    }

    if (apks.isEmpty()) {
      throw new ActivityLocatorException("No APKs provided. Unable to extract default activity");
    }

    String defaultActivity = computeDefaultActivityFromApks(apks, myApplicationId);
    if (defaultActivity == null) {
      throw new ActivityLocatorException(AndroidBundle.message("default.activity.not.found.error"));
    }

    return defaultActivity;
  }

  // Open all archives ending with ".apks". Attempt to find the AndroidManifest.xml entry.
  // Parse it and add activities to the list. When all have been retrieved, attempt to find
  // the Default Activity.
  private static String computeDefaultActivityFromApks(@NotNull Collection<ApkInfo> apks, @NotNull String applicationId) {
    List<ApkInfo> filteredApks = apks.stream()
      .filter(apk -> apk.getApplicationId().equals(applicationId))
      .collect(Collectors.toUnmodifiableList());

    if (filteredApks.size() != 1) {
      StringBuilder errorMessage = new StringBuilder();
      if (filteredApks.isEmpty()) {
        errorMessage.append("No matching APK for application: " + applicationId + "\n");
      } else {
        errorMessage.append("Multiple APKs present for application: " + applicationId + "\n");
        errorMessage.append("Projects:\n");
        for (ApkInfo apkInfo : apks) {
          errorMessage.append("  " + apkInfo.getApplicationId() + " containing :\n");
          for (ApkFileUnit fileUnit : apkInfo.getFiles()) {
            errorMessage.append("    " + fileUnit.getApkFile() + "\n");
          }
        }
      }
      throw new IllegalStateException(errorMessage.toString());
    }

    List<NodeActivity> activities = new ArrayList<>();
    ApkInfo apkInfo = filteredApks.iterator().next();
    for (ApkFileUnit apkFileUnit : apkInfo.getFiles()) {
      // Only process .apk archives.
      File file = apkFileUnit.getApkFile();
      String ext = file.getName().toLowerCase(Locale.US);
      if (!ext.endsWith(".apk")) {
        continue;
      }

      // Open all apks and par´AndroidManifest.xml if found.
      try (ZipFile zipFile = new ZipFile(file)) {
        ZipEntry manifestEntry = zipFile.getEntry(SdkConstants.FN_ANDROID_MANIFEST_XML);
        if (manifestEntry == null) {
          continue;
        }

        // Parse manifest (assumed to be in BinaryFormat, since it is retrieved from an APK)
        try (InputStream input = zipFile.getInputStream(manifestEntry)) {
          ManifestInfo manifest = ManifestInfo.parseBinaryFromStream(input);
          activities.addAll(manifest.activities().stream().map(NodeActivity::new).collect(Collectors.toList()));
        }
      }
      catch (Exception e) {
        LOG.warn("Unable to parse '" + file.getName() + "' for default activity", e);
        continue;
      }
    }

    String defaultActivityName = DefaultActivityLocator.computeDefaultActivity(activities);

    // Useful information to investigate bug reports
    if (defaultActivityName == null) {
      StringBuilder errorMessage = new StringBuilder("Unable to find Default Activity in:\n");
      printActivities(activities, errorMessage);
      LOG.info(errorMessage.toString());
    }
    return defaultActivityName;
  }

  private static void printActivities(List<NodeActivity> activities, StringBuilder message) {
    for (NodeActivity activity : activities) {
      message.append("  " + activity.getQualifiedName() + ":\n");
      for (IntentFilter intent : activity.getIntentFilters()) {
        for (String action : intent.getActions()) {
          message.append("    " + action + "\n");
        }
        for (String category : intent.getCategories()) {
          message.append("    " + category + "\n");
        }
      }
    }
  }
}
