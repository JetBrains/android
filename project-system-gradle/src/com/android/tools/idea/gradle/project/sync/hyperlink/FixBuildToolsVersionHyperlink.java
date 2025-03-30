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

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.issues.processor.FixBuildToolsProcessor;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class FixBuildToolsVersionHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final List<VirtualFile> myBuildFiles;
  @NotNull private final String myVersion;
  /**
   * Whether or not to remove the buildToolsVersion line from Gradle files, if false we update them to myVersion instead.
   */
  private final boolean myRemoveBuildTools;

  public FixBuildToolsVersionHyperlink(@NotNull String version, @NotNull List<VirtualFile> buildFiles, boolean removeBuildTools) {
    super("fix.build.tools.version",
          (removeBuildTools ? "Remove" : "Update") + " Build Tools version and sync project",
          AndroidStudioEvent.GradleSyncQuickFix.FIX_BUILD_TOOLS_VERSION_HYPERLINK);
    myBuildFiles = buildFiles;
    myVersion = version;
    myRemoveBuildTools = removeBuildTools;
  }

  @Override
  protected void execute(@NotNull Project project) {
    FixBuildToolsProcessor processor = new FixBuildToolsProcessor(project, myBuildFiles, myVersion, true, myRemoveBuildTools);
    processor.setPreviewUsages(true);
    processor.run();
  }
}
