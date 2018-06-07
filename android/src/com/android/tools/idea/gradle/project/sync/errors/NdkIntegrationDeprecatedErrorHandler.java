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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class NdkIntegrationDeprecatedErrorHandler extends BaseSyncErrorHandler {
  private static final String NDK_INTEGRATION_DEPRECATED = "NDK integration is deprecated in the current plugin.";

  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (isNotEmpty(text) && getFirstLineMessage(text).contains(NDK_INTEGRATION_DEPRECATED)) {
      updateUsageTracker();
      return NDK_INTEGRATION_DEPRECATED;
    }
    return null;
  }

  @Override
  @NotNull
  protected List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    hyperlinks.add(new OpenUrlHyperlink("https://developer.android.com/studio/build/experimental-plugin.html",
                                        "Consider trying the new experimental plugin"));
    hyperlinks.add(new SetUseDeprecatedNdkHyperlink());
    return hyperlinks;
  }

  public static class SetUseDeprecatedNdkHyperlink extends NotificationHyperlink {
    public SetUseDeprecatedNdkHyperlink() {
      super("useDeprecatedNdk",
            "Set \"android.useDeprecatedNdk=true\" in gradle.properties to continue using the current NDK integration");
    }

    @Override
    protected void execute(@NotNull Project project) {
      GradleProperties gradleProperties;
      try {
        gradleProperties = new GradleProperties(project);
      }
      catch (IOException e) {
        Messages.showErrorDialog(project, "Failed to read gradle.properties: " + e.getMessage(), "Quick Fix");
        return;
      }

      gradleProperties.getProperties().setProperty("android.useDeprecatedNdk", "true");

      try {
        gradleProperties.save();
      }
      catch (IOException e) {
        Messages.showErrorDialog(project, "Failed to update gradle.properties: " + e.getMessage(), "Quick Fix");
        return;
      }

      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
    }
  }
}