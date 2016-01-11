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
package com.android.tools.idea.welcome.install;

import com.android.repository.api.RemotePackage;
import com.android.tools.idea.npw.ImportUIUtil;
import com.google.common.collect.Lists;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Install or updates SDK components if needed.
 */
public class InstallComponentsOperation extends InstallOperation<File, File> {
  @NotNull private final Logger LOG = Logger.getInstance(getClass());
  @NotNull private final ComponentInstaller myComponentInstaller;
  @NotNull private Collection<? extends InstallableComponent> myComponents;

  public InstallComponentsOperation(@NotNull InstallContext context,
                                    @NotNull Collection<? extends InstallableComponent> components,
                                    @NotNull ComponentInstaller componentInstaller,
                                    double progressRatio) {
    super(context, progressRatio);
    myComponentInstaller = componentInstaller;
    myComponents = components;
  }

  @Nullable
  private static String getRetryMessage(Collection<RemotePackage> packages) {
    if (!packages.isEmpty()) {
      List<String> descriptions = Lists.newArrayList();
      for (RemotePackage p : packages) {
        descriptions.add(p.getDisplayName());
      }
      return ImportUIUtil.formatElementListString(descriptions,
                                                  "The following SDK component was not installed: %s",
                                                  "The following SDK components were not installed: %1$s and %2$s",
                                                  "%1$s and %2$s more SDK components were not installed");
    }
    else {
      return null;
    }
  }

  @NotNull
  @Override
  protected File perform(@NotNull ProgressIndicator indicator, @NotNull File sdkLocation) throws WizardException {
    indicator.setText("Checking for updated SDK components");
    List<RemotePackage> packages = myComponentInstaller.getPackagesToInstall(myComponents);
    while (!packages.isEmpty()) {
      SdkManagerProgressIndicatorIntegration logger = new SdkManagerProgressIndicatorIntegration(indicator, myContext);
      myComponentInstaller.installPackages(packages, logger);
      // If we didn't set remote information on the installer we assume we weren't expecting updates. So set false for
      // defaultUpdateAvailable so we don't think everything failed to install.
      packages = myComponentInstaller.getPackagesToInstall(myComponents);
      String message = getRetryMessage(packages);
      if (message != null) {
        promptToRetry(message, logger.getErrors(), null);
      }
    }
    myContext.print("Android SDK is up to date.\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    indicator.setFraction(1.0); // 100%
    return sdkLocation;
  }

  @Override
  public void cleanup(@NotNull File result) {
    // Nothing here to do
  }
}
