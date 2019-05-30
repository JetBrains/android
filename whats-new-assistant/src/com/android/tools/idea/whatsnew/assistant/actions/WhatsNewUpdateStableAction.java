/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.actions;

import com.android.tools.idea.assistant.AssistActionHandler;
import com.android.tools.idea.assistant.datamodel.ActionData;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.CheckForUpdateResult;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateInstaller;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.updateSettings.impl.UpdateStrategy;
import com.intellij.openapi.updateSettings.impl.UpdatesInfo;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.URLUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class WhatsNewUpdateStableAction implements AssistActionHandler {
  static final String ACTION_KEY = "wna.update.stable";

  @NotNull
  @Override
  public String getId() {
    return ACTION_KEY;
  }

  @Override
  public void handleAction(@NotNull ActionData actionData, @NotNull Project project) {
    // TODO: b/126605387 Collect metrics
    // TODO: b/126602033 Use methods from UpdateChecker after it's refactored, to avoid showing the extra update info dialog
    UpdateChecker.updateAndShowResult(project, null);
  }
}
