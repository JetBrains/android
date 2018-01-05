/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.prefs.AndroidLocation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.utils.ILogger;
import com.intellij.CommonBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetScopedService;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ModuleAvds extends AndroidFacetScopedService {
  private static final Key<ModuleAvds> KEY = Key.create(ModuleAvds.class.getName());

  private AvdManager myAvdManager;

  @NotNull
  public static ModuleAvds getInstance(@NotNull AndroidFacet facet) {
    ModuleAvds avds = facet.getUserData(KEY);
    if (avds == null) {
      avds = new ModuleAvds(facet);
      facet.putUserData(KEY, avds);
    }
    return avds;
  }

  public static void disposeInstance(@NotNull AndroidFacet facet) {
    ModuleAvds avds = facet.getUserData(KEY);
    if (avds != null) {
      Disposer.dispose(avds);
    }
  }

  private ModuleAvds(@NotNull AndroidFacet facet) {
    super(facet);
  }

  @NotNull
  public AvdInfo[] getAllAvds() {
    AvdManager manager = getAvdManagerSilently();
    if (manager != null) {
      if (reloadAvds(manager)) {
        return manager.getAllAvds();
      }
    }
    return new AvdInfo[0];
  }

  private boolean reloadAvds(@NotNull AvdManager manager) {
    Project project = getModule().getProject();
    try {
      MessageBuildingSdkLog log = new MessageBuildingSdkLog();
      manager.reloadAvds(log);
      if (!log.getErrorMessage().isEmpty()) {
        String message = AndroidBundle.message("cant.load.avds.error.prefix") + ' ' + log.getErrorMessage();
        Messages.showErrorDialog(project, message, CommonBundle.getErrorTitle());
      }
      return true;
    }
    catch (AndroidLocation.AndroidLocationException e) {
      Messages.showErrorDialog(project, AndroidBundle.message("cant.load.avds.error"), CommonBundle.getErrorTitle());
    }
    return false;
  }

  @NotNull
  public AvdInfo[] getValidCompatibleAvds() {
    AvdManager manager = getAvdManagerSilently();
    List<AvdInfo> result = new ArrayList<>();
    if (manager != null && reloadAvds(manager)) {
      addCompatibleAvds(result, manager.getValidAvds());
    }
    return result.toArray(new AvdInfo[result.size()]);
  }

  @NotNull
  private AvdInfo[] addCompatibleAvds(@NotNull List<AvdInfo> to, @NotNull AvdInfo[] from) {
    AndroidVersion minSdk = AndroidModuleInfo.getInstance(getFacet()).getRuntimeMinSdkVersion();
    AndroidPlatform platform = getFacet().getConfiguration().getAndroidPlatform();
    if (platform == null) {
      Logger.getInstance(getClass()).error("Android Platform not set for module: " + getModule().getName());
      return new AvdInfo[0];
    }

    for (AvdInfo avd : from) {
      ISystemImage systemImage = avd.getSystemImage();
      if (systemImage == null ||
          LaunchCompatibility.canRunOnAvd(minSdk, platform.getTarget(), systemImage).isCompatible() != ThreeState.NO) {
        to.add(avd);
      }
    }
    return to.toArray(new AvdInfo[to.size()]);
  }

  @Nullable
  public AvdManager getAvdManagerSilently() {
    try {
      return getAvdManager(new AvdManagerLog());
    }
    catch (AndroidLocation.AndroidLocationException ignored) {
    }
    return null;
  }

  @Nullable
  public AvdManager getAvdManager(@NotNull ILogger log) throws AndroidLocation.AndroidLocationException {
    if (myAvdManager == null) {
      myAvdManager = AvdManager.getInstance(AndroidSdkData.getSdkHolder(getFacet()), log);
    }
    return myAvdManager;
  }

  @Override
  protected void onServiceDisposal(@NotNull AndroidFacet facet) {
    facet.putUserData(KEY, null);
  }
}
