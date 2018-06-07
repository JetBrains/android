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
import com.android.sdklib.internal.avd.AvdManager;
import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AvdManagerLog;
import org.jetbrains.android.sdk.MessageBuildingSdkLog;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AvdManagerUtils {
  public static boolean reloadAvds(@NotNull AvdManager manager, @NotNull Project project) {
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
      return false;
    }
  }

  @Nullable
  public static AvdManager getAvdManagerSilently(@NotNull AndroidFacet facet) {
    try {
      return AvdManager.getInstance(AndroidSdkData.getSdkHolder(facet), new AvdManagerLog());
    }
    catch (AndroidLocation.AndroidLocationException ignored) {
    }
    return null;
  }
}
