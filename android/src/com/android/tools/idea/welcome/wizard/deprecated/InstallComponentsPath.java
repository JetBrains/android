/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.welcome.install.WizardException;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InstallComponentsPath {
  /**
   * Returns the latest platform from a given list.
   *
   * It is possible to select whether one wants the last extension of the latest platform or whether
   * one wants the latest base extension.
   *
   *
   * @param remotePackages the list of packages to search for the last platform.
   * @param returnBaseExtension whether to always return the base extension of the latest platform.
   * @return
   */
  @Nullable
  public static RemotePackage findLatestPlatform(
    @NotNull Collection<RemotePackage> remotePackages,
    boolean returnBaseExtension
  ) {
    AndroidVersion max = null;
    RemotePackage latest = null;
    for (RemotePackage pkg : remotePackages) {
      TypeDetails details = pkg.getTypeDetails();
      if (!(details instanceof DetailsTypes.PlatformDetailsType)) {
        continue;
      }
      DetailsTypes.PlatformDetailsType platformDetails = (DetailsTypes.PlatformDetailsType)details;
      AndroidVersion version = platformDetails.getAndroidVersion();
      if (version.isPreview() || (returnBaseExtension && !version.isBaseExtension())) {
        // We only want stable platforms, and possibly only base extension if requested
        continue;
      }
      if (max == null || version.compareTo(max) > 0) {
        latest = pkg;
        max = version;
      }
    }
    return latest;
  }

  public static File createTempDir() throws WizardException {
    File tempDirectory;
    try {
      tempDirectory = FileUtil.createTempDirectory("AndroidStudio", "FirstRun", true);
    }
    catch (IOException e) {
      throw new WizardException("Unable to create temporary folder: " + e.getMessage(), e);
    }
    return tempDirectory;
  }
}
