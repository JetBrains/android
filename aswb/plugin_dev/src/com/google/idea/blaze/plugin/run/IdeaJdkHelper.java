/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.plugin.run;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import javax.annotation.Nullable;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.run.IdeaLicenseHelper;

/** Contains all dependencies on devkit (not included in plugin SDK) */
public class IdeaJdkHelper {

  public static boolean isIdeaJdk(@Nullable Sdk sdk) {
    return sdk != null && isIdeaJdkType(sdk.getSdkType());
  }

  public static boolean isIdeaJdkType(SdkTypeId type) {
    return IdeaJdk.getInstance().equals(type);
  }

  @Nullable
  public static String getBuildNumber(Sdk sdk) {
    return IdeaJdk.getBuildNumber(sdk.getHomePath());
  }

  public static void copyIDEALicense(final String sandboxHome) {
    IdeaLicenseHelper.copyIDEALicense(sandboxHome);
  }

  /** @throws RuntimeException if input Sdk is not an IdeaJdk */
  public static String getSandboxHome(Sdk sdk) {
    if (!isIdeaJdk(sdk)) {
      throw new RuntimeException("Invalid SDK type: " + sdk.getSdkType());
    }
    return ((Sandbox) sdk.getSdkAdditionalData()).getSandboxHome();
  }

  @Nullable
  public static String getPlatformPrefix(String buildNumber) {
    return IntelliJPlatformProduct.fromBuildNumber(buildNumber).getPlatformPrefix();
  }
}
