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
package com.android.tools.idea.updater.configure;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdkManagerEnabled;

/**
 * Action to open the Android SDK pane in Settings.
 */
public class RunSdkConfigAction extends DumbAwareAction {
  protected RunSdkConfigAction() {
    super(AndroidBundle.message("android.run.sdk.manager.action.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(isAndroidSdkManagerEnabled());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UsageTracker.log(AndroidStudioEvent.newBuilder()
                                   .setCategory(EventCategory.SDK_MANAGER)
                                   .setKind(AndroidStudioEvent.EventKind.SDK_MANAGER_TOOLBAR_CLICKED));
    if (ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      // Invoked from Welcome Screen, might not have an SDK setup yet
      AndroidSdkData sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
      if (sdkData == null) {
        // This probably shouldn't happen, but the check was there in the standalone launcher case...
        return;
      }
    }
    Configurable configurable =
      ConfigurableExtensionPointUtil.createApplicationConfigurableForProvider(SdkUpdaterConfigurableProvider.class);
    ShowSettingsUtil.getInstance().showSettingsDialog(null, configurable.getClass());
  }
}
