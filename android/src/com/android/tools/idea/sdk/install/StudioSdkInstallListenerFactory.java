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
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.installer.SdkInstallListenerFactory;
import com.android.tools.idea.welcome.install.Aehd;
import com.android.tools.idea.welcome.install.Haxm;
import java.util.List;
import org.jetbrains.annotations.NotNull;

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
    result.add(new VfsInstallListener());
    if (p.getPath().equals(Haxm.InstallerInfo.getRepoPackagePath())) {
      result.add(new VmInstallListener(VmType.HAXM));
    }
    if (p.getPath().equals(Aehd.InstallerInfo.getRepoPackagePath())) {
      result.add(new VmInstallListener(VmType.AEHD));
    }
    if (p.getPath().equals(SdkConstants.FD_PLATFORM_TOOLS)) {
      result.add(new PlatformToolsInstallListener(getSdkHandler()));
    }
    return result;
  }

}
