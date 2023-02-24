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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.project.sync.errors.SdkBuildToolsTooLowIssueChecker;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixBuildToolsVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.android.repository.Revision.parseRevision;
import static com.android.sdklib.repository.meta.DetailsTypes.getBuildToolsPath;
import static com.android.tools.idea.gradle.project.sync.errors.SdkBuildToolsTooLowIssueCheckerKt.doesAndroidGradlePluginPackageBuildTools;

class BuildToolsTooLowReporter extends SimpleDeduplicatingSyncIssueReporter {

  @Override
  int getSupportedIssueType() {
    return IdeSyncIssue.TYPE_BUILD_TOOLS_TOO_LOW;
  }

  @NotNull
  @Override
  protected List<SyncIssueNotificationHyperlink> getCustomLinks(@NotNull Project project,
                                                                @NotNull List<IdeSyncIssue> syncIssues,
                                                                @NotNull List<Module> affectedModules,
                                                                @NotNull Map<Module, VirtualFile> buildFileMap) {
    assert !syncIssues.isEmpty() && !affectedModules.isEmpty();
    String minimumVersion = syncIssues.get(0).getData();
    if (minimumVersion == null) {
      return ImmutableList.of();
    }

    return getQuickFixHyperlinks(minimumVersion, affectedModules, buildFileMap);
  }

  @NotNull
  public List<SyncIssueNotificationHyperlink> getQuickFixHyperlinks(@NotNull String minimumVersion,
                                                                    @NotNull List<Module> affectedModules,
                                                                    @NotNull Map<Module, VirtualFile> buildFileMap) {
    List<SyncIssueNotificationHyperlink> hyperlinks = new ArrayList<>();
    boolean buildToolInstalled = false;

    AndroidSdkHandler sdkHandler = null;
    AndroidSdkData androidSdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (androidSdkData != null) {
      sdkHandler = androidSdkData.getSdkHandler();
    }

    if (sdkHandler != null) {
      ProgressIndicator progress = new StudioLoggerProgressIndicator(SdkBuildToolsTooLowIssueChecker.class);
      RepositoryPackages packages = sdkHandler.getSdkManager(progress).getPackages();
      LocalPackage buildTool = packages.getLocalPackages().get(getBuildToolsPath(parseRevision(minimumVersion)));
      buildToolInstalled = buildTool != null;
    }


    List<VirtualFile> buildFiles =
      affectedModules.stream().map(m -> buildFileMap.get(m)).filter(Objects::nonNull).collect(Collectors.toList());

    if (!buildToolInstalled) {
      hyperlinks
        .add(new InstallBuildToolsHyperlink(minimumVersion, buildFiles, doesAndroidGradlePluginPackageBuildTools(affectedModules)));
    }
    else if (!buildFiles.isEmpty()) {
      hyperlinks
        .add(new FixBuildToolsVersionHyperlink(minimumVersion, buildFiles, doesAndroidGradlePluginPackageBuildTools(affectedModules)));
    }

    return hyperlinks;
  }
}
