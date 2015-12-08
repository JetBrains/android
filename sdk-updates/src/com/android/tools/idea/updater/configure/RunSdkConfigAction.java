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

import com.android.tools.idea.stats.UsageTracker;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidSdkManagerEnabled;

/**
 * Action to open the Android SDK pane in Settings.
 */
public class RunSdkConfigAction extends DumbAwareAction {
  protected RunSdkConfigAction() {
    super(AndroidBundle.message("android.run.sdk.manager.action.text"));
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isAndroidSdkManagerEnabled());
  }

  @Override
  public void actionPerformed(@Nullable AnActionEvent e) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_SDK_MANAGER, UsageTracker.ACTION_SDK_MANAGER_TOOLBAR_CLICKED, null, null);
    Configurable configurable =
      ConfigurableExtensionPointUtil.createApplicationConfigurableForProvider(SdkUpdaterConfigurableProvider.class);
    ShowSettingsUtil.getInstance().showSettingsDialog(null, configurable.getClass());
  }
}
