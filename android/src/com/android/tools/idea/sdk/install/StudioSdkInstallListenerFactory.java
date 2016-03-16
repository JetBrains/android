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
package com.android.tools.idea.sdk.install;

import com.android.SdkConstants;
import com.android.repository.api.InstallerFactory;
import com.android.repository.api.PackageOperation;
import com.android.repository.api.RepoPackage;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.installer.SdkInstallListenerFactory;
import com.android.tools.idea.welcome.install.Haxm;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link InstallerFactory.StatusChangeListenerFactory} that creates appropriate {@link PackageOperation.StatusChangeListener}s for
 * installers run within Studio.
 */
public class StudioSdkInstallListenerFactory extends SdkInstallListenerFactory {
  public StudioSdkInstallListenerFactory(@NotNull AndroidSdkHandler handler) {
    super(handler);
  }

  @NotNull
  @Override
  public List<PackageOperation.StatusChangeListener> createListeners(@NotNull RepoPackage p) {
    List<PackageOperation.StatusChangeListener> result = super.createListeners(p);
    if (p.getPath().equals(Haxm.REPO_PACKAGE_PATH)) {
      result.add(new HaxmInstallListener());
    }
    if (p.getPath().equals(SdkConstants.FD_PLATFORM_TOOLS)) {
      result.add(new PlatformToolsInstallListener(getSdkHandler()));
    }
    return result;
  }

}
