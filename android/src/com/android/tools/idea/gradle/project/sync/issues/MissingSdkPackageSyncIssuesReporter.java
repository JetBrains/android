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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallSdkPackageHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Reporter for dealing with {@link IdeSyncIssue#TYPE_MISSING_SDK_PACKAGE} issues.
 */
public class MissingSdkPackageSyncIssuesReporter extends SimpleDeduplicatingSyncIssueReporter {
  @Override
  int getSupportedIssueType() {
    return IdeSyncIssue.TYPE_MISSING_SDK_PACKAGE;
  }

  @Override
  protected boolean shouldIncludeModuleLinks() {
    return false;
  }

  @NotNull
  @Override
  protected List<NotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                       @NotNull List<IdeSyncIssue> syncIssues,
                                                       @NotNull List<Module> affectedModules,
                                                       @NotNull Map<Module, VirtualFile> buildFileMap) {
    assert !syncIssues.isEmpty();
    String data = syncIssues.get(0).getData();
    if (data != null) {
      return ImmutableList.of(new InstallSdkPackageHyperlink(Arrays.asList(data.split(" "))));
    }
    return ImmutableList.of();
  }
}
