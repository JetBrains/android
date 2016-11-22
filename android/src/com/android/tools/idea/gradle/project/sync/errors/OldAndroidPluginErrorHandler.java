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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.plugin.AndroidPluginInfo.searchInBuildFilesOnly;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class OldAndroidPluginErrorHandler extends BaseSyncErrorHandler {
  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull NotificationData notification, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && getFirstLineMessage(text).startsWith("Plugin is too old, please update to a more recent version")) {
      updateUsageTracker();
      // This way we remove extra lines and spaces from original message.
      return Joiner.on('\n').join(Splitter.on('\n').omitEmptyStrings().trimResults().splitToList(text));
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull NotificationData notification,
                                                              @NotNull Project project,
                                                              @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(new FixAndroidGradlePluginVersionHyperlink());
    AndroidPluginInfo result = searchInBuildFilesOnly(project);
    if (result != null && result.getPluginBuildFile() != null) {
      hyperlinks.add(new OpenFileHyperlink(result.getPluginBuildFile().getPath()));
    }
    return hyperlinks;
  }
}