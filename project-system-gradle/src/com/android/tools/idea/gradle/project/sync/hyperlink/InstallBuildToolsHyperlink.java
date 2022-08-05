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

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_BUILD_TOOLS_INSTALLED;

import com.android.repository.Revision;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.processor.FixBuildToolsProcessor;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class InstallBuildToolsHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final String myVersion;
  @NotNull private final List<VirtualFile> myBuildFiles;
  /**
   * Whether or not to remove the buildToolsVersion line from Gradle files, if false we update them to myVersion instead.
   */
  private final boolean myRemoveBuildTools;

  public InstallBuildToolsHyperlink(@NotNull String version) {
    this(version, ImmutableList.of(), false);
  }

  public InstallBuildToolsHyperlink(@NotNull String version, @NotNull List<VirtualFile> buildFiles, boolean removeBuildTools) {
    super("install.build.tools",
          getText(version, !buildFiles.isEmpty()),
          AndroidStudioEvent.GradleSyncQuickFix.INSTALL_BUILD_TOOLS_HYPERLINK);
    myBuildFiles = buildFiles;
    myVersion = version;
    myRemoveBuildTools = removeBuildTools;
  }

  @NotNull
  private static String getText(@NotNull String version, boolean hasBuildFiles) {
    String msg = String.format("Install Build Tools %1$s", version);
    if (hasBuildFiles) {
      msg += ", update version in build file and sync project";
    }
    else {
      msg += " and sync project";
    }
    return msg;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<String> requested = new ArrayList<>();
    Revision minBuildToolsRev = Revision.parseRevision(myVersion);
    requested.add(DetailsTypes.getBuildToolsPath(minBuildToolsRev));
    ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, requested);
    if (dialog != null && dialog.showAndGet()) {
      if (!myBuildFiles.isEmpty()) {
        FixBuildToolsProcessor processor = new FixBuildToolsProcessor(project, myBuildFiles, myVersion, true, myRemoveBuildTools);
        processor.setPreviewUsages(true);
        processor.run();
      }
      else {
        HyperlinkUtil.requestProjectSync(project, TRIGGER_QF_BUILD_TOOLS_INSTALLED);
      }
    }
  }
}
