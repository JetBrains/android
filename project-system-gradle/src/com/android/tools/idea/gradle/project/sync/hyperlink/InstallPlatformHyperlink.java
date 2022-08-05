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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_PLATFORM_INSTALLED;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class InstallPlatformHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final AndroidVersion[] myAndroidVersions;

  public InstallPlatformHyperlink(@NotNull Collection<AndroidVersion> androidVersions) {
    this(androidVersions.toArray(new AndroidVersion[0]));
  }

  public InstallPlatformHyperlink(@NotNull AndroidVersion... androidVersions) {
    super("install.android.platform",
          "Install missing platform(s) and sync project",
          AndroidStudioEvent.GradleSyncQuickFix.INSTALL_PLATFORM_HYPERLINK);
    myAndroidVersions = androidVersions;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<String> requested = new ArrayList<>();
    for (AndroidVersion version : myAndroidVersions) {
      requested.add(DetailsTypes.getPlatformPath(version));
    }
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
    if (dialog != null && dialog.showAndGet()) {
      HyperlinkUtil.requestProjectSync(project, TRIGGER_QF_PLATFORM_INSTALLED);
    }
  }
}
