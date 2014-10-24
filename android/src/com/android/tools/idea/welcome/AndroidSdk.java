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

import com.android.annotations.VisibleForTesting;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Android SDK installable component.
 */
public final class AndroidSdk extends InstallableComponent {
  public static final long SIZE = 2300 * Storage.Unit.MiB.getNumberOfBytes();
  private static final ScopedStateStore.Key<Boolean> KEY_INSTALL_SDK =
    ScopedStateStore.createKey("download.sdk", ScopedStateStore.Scope.PATH, Boolean.class);

  public AndroidSdk() {
    super("Android SDK", SIZE, KEY_INSTALL_SDK);
  }

  @VisibleForTesting
  static PkgDesc.Builder[] getPackages() {
    AndroidVersion lVersion = new AndroidVersion(21, null);
    MajorRevision unspecifiedRevision = new MajorRevision(FullRevision.NOT_SPECIFIED);

    PkgDesc.Builder androidSdkTools = PkgDesc.Builder.newTool(FullRevision.NOT_SPECIFIED, FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder androidSdkPlatformTools = PkgDesc.Builder.newPlatformTool(FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder androidSdkBuildTools = PkgDesc.Builder.newBuildTool(new FullRevision(21, 0, 2));
    PkgDesc.Builder supportRepository = InstallComponentsPath.createExtra(true, "android", "m2repository");
    PkgDesc.Builder googleRepository = InstallComponentsPath.createExtra(true, "google", "m2repository");
    PkgDesc.Builder atomImage = PkgDesc.Builder.newSysImg(lVersion, new IdDisplay("default", ""), "x86", unspecifiedRevision);
    PkgDesc.Builder platform = PkgDesc.Builder.newPlatform(lVersion, unspecifiedRevision, FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder sample = PkgDesc.Builder.newSample(lVersion, unspecifiedRevision, FullRevision.NOT_SPECIFIED);
    PkgDesc.Builder platformSources = PkgDesc.Builder.newSource(lVersion, unspecifiedRevision);
    PkgDesc.Builder usb = InstallComponentsPath.createExtra(SystemInfo.isLinux || SystemInfo.isWindows, "google", "usb_driver");

    return new PkgDesc.Builder[]{androidSdkTools, androidSdkPlatformTools, androidSdkBuildTools, supportRepository, googleRepository,
      atomImage, platform, sample, platformSources, usb};
  }

  @NotNull
  @Override
  public PkgDesc.Builder[] getRequiredSdkPackages() {
    return getPackages();
  }

  @Override
  public void init(@NotNull ScopedStateStore state, @NotNull ProgressStep progressStep) {
    state.put(KEY_INSTALL_SDK, true);
  }

  @Override
  public DynamicWizardStep[] createSteps() {
    return new DynamicWizardStep[]{};
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull File sdk) {
    // Nothing to do, having components installed is enough
  }

  @Override
  public boolean isOptional() {
    return false;
  }
}
