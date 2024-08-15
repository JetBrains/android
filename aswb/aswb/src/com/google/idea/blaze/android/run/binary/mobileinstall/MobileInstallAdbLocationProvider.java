/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.binary.mobileinstall;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Optional;
import org.jetbrains.android.sdk.AndroidSdkUtils;

/** Provides location of the adb binary. */
public interface MobileInstallAdbLocationProvider {
  ExtensionPointName<MobileInstallAdbLocationProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.MobileInstallAdbLocationProvider");

  /** Returns the path to adb if it should be provided to MI via the --adb flag, null otherwise . */
  Optional<String> getAdbLocation(Project project);

  /** Returns location of the adb binary suitable for the given project. */
  static Optional<String> getAdbLocationForMobileInstall(Project project) {
    for (MobileInstallAdbLocationProvider provider : EP_NAME.getExtensions()) {
      return provider.getAdbLocation(project);
    }
    File adb = AndroidSdkUtils.getAdb(project);
    return adb == null ? Optional.empty() : Optional.of(adb.getAbsolutePath());
  }
}
