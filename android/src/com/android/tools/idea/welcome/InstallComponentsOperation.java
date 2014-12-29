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
package com.android.tools.idea.welcome;

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.sdklib.repository.remote.RemotePkgInfo;
import com.android.tools.idea.avdmanager.LogWrapper;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Install or updates SDK components if needed.
 */
public class InstallComponentsOperation extends InstallOperation<File, File> {
  @NotNull private final Logger LOG = Logger.getInstance(getClass());
  @Nullable private Multimap<PkgType, RemotePkgInfo> myRemotePackages;
  @NotNull private Collection<? extends InstallableComponent> myComponents;

  protected InstallComponentsOperation(@NotNull InstallContext context,
                                       @NotNull Collection<? extends InstallableComponent> components,
                                       @Nullable Multimap<PkgType, RemotePkgInfo> remotePackages,
                                       double progressRatio) {
    super(context, progressRatio);
    myRemotePackages = remotePackages;
    myComponents = components;
  }

  @Nullable
  private static String getRetryMessage(ArrayList<String> packages) {
    String message = null;
    if (packages.size() == 1) {
      message = String.format("The following SDK component was not installed: %s", Iterables.getFirst(packages, null));
    }
    else if (!packages.isEmpty()) {
      message = String.format("The following SDK components were not installed: %s", Joiner.on(", ").join(packages));
    }
    return message;
  }

  @NotNull
  @Override
  protected File perform(@NotNull ProgressIndicator indicator, @NotNull File sdkLocation) throws WizardException {
    ComponentInstaller componentInstaller = new ComponentInstaller(myComponents, myRemotePackages);
    SdkManager manager = SdkManager.createManager(sdkLocation.getAbsolutePath(), new LogWrapper(LOG));
    if (manager != null) {
      indicator.setText("Checking for updated SDK components");
      ArrayList<String> packages = componentInstaller.getPackagesToInstall(manager);
      while (!packages.isEmpty()) {
        ILogger logger = new SdkManagerProgressIndicatorIntegration(indicator, myContext, packages.size());
        componentInstaller.installPackages(manager, packages, logger);
        manager.reloadSdk(new LogWrapper(LOG));
        packages = componentInstaller.getPackagesToInstall(manager);
        String message = getRetryMessage(packages);
        if (message != null) {
          promptToRetry(message, message, null);
        }
      }
    }
    else {
      throw new WizardException("Corrupt SDK installation");
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
