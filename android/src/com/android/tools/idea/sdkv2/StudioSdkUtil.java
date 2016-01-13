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
package com.android.tools.idea.sdkv2;

import com.android.repository.api.RemotePackage;
import com.android.repository.impl.installer.PackageInstaller;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.welcome.install.Haxm;
import org.jetbrains.annotations.NotNull;

/**
 * Studio-specific SDK-related utilities
 */
public final class StudioSdkUtil {

  /**
   * Find the best {@link PackageInstaller} for the given {@link RemotePackage}.
   */
  @NotNull
  public static PackageInstaller findBestInstaller(@NotNull RemotePackage p) {
    if (p.getPath().equals(Haxm.REPO_PACKAGE_PATH)) {
      return new HaxmInstaller();
    }
    return AndroidSdkHandler.findBestInstaller(p);
  }

  private StudioSdkUtil() {}
}
