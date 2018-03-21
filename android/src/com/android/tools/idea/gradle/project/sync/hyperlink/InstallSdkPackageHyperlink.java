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

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

/**
 * A {@link NotificationHyperlink} that offers the user to install the SDK package that Gradle decided to be required but missing.
 */
public class InstallSdkPackageHyperlink extends NotificationHyperlink {
  private final String myPackageId;

  public InstallSdkPackageHyperlink(@NotNull String packageId) {
    super("install.sdk.package", String.format("Install the %s SDK package", packageId));
    myPackageId = packageId;
  }

  @Override
  protected void execute(@NotNull Project project) {
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, Collections.singletonList(myPackageId));
    if (dialog != null && dialog.showAndGet()) {
      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
    }
  }
}
