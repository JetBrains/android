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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class InstallPlatformHyperlink extends NotificationHyperlink {
  @NotNull private final AndroidVersion[] myAndroidVersions;

  public InstallPlatformHyperlink(@NotNull AndroidVersion... androidVersions) {
    super("install.android.platform", "Install missing platform(s) and sync project");
    myAndroidVersions = androidVersions;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<String> requested = Lists.newArrayList();
    for (AndroidVersion version : myAndroidVersions) {
      requested.add(DetailsTypes.getPlatformPath(version));
    }
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
    if (dialog != null && dialog.showAndGet()) {
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }
}
