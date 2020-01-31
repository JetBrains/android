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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_REPOSITORY_INSTALLED;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.project.Project;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link NotificationHyperlink} that offers the user to install the SDK package that Gradle decided to be required but missing.
 */
public class InstallSdkPackageHyperlink extends NotificationHyperlink {
  private final List<String> myPackageIds;

  public InstallSdkPackageHyperlink(@NotNull List<String> packageIds) {
    super("install.sdk.package", "Install missing SDK package(s)");
    myPackageIds = packageIds;
  }

  @Override
  protected void execute(@NotNull Project project) {
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, myPackageIds, true);
    if (dialog != null && dialog.showAndGet()) {
      GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_REPOSITORY_INSTALLED);
    }
  }
}
