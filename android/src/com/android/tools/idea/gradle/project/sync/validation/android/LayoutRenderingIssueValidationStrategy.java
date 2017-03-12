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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenUrlHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.android.tools.idea.project.messages.MessageType.WARNING;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;

public class LayoutRenderingIssueValidationStrategy extends AndroidProjectValidationStrategy {
  @Nullable private GradleVersion myModelVersion;

  LayoutRenderingIssueValidationStrategy(@NotNull Project project) {
    super(project);
  }

  @Override
  void validate(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
    if (androidModel.getFeatures().isLayoutRenderingIssuePresent()) {
      myModelVersion = androidModel.getModelVersion();
    }
  }

  @Override
  void fixAndReportFoundIssues() {
    if (myModelVersion != null) {
      String text = String.format("Using an obsolete version of the Gradle plugin (%1$s);", myModelVersion);
      text += " this can lead to layouts not rendering correctly.";

      SyncMessage message = new SyncMessage(DEFAULT_GROUP, WARNING, text);
      message.add(Arrays.asList(new FixAndroidGradlePluginVersionHyperlink(),
                                new OpenUrlHyperlink("https://code.google.com/p/android/issues/detail?id=170841", "More Info...")));

      GradleSyncMessages.getInstance(getProject()).report(message);
    }
  }

  @VisibleForTesting
  @Nullable
  GradleVersion getModelVersion() {
    return myModelVersion;
  }

  @VisibleForTesting
  void setModelVersion(@Nullable GradleVersion modelVersion) {
    myModelVersion = modelVersion;
  }
}
