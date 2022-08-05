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

import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import java.awt.Window;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ShowSyncIssuesDetailsHyperlink extends SyncIssueNotificationHyperlink {
  @NotNull private final String myMessage;
  @NotNull private final List<String> myDetails;

  private SyncIssueDetailsDialog myDetailsDialog;

  public ShowSyncIssuesDetailsHyperlink(@NotNull String message, @NotNull List<String> details) {
    super(message, "Show Details", AndroidStudioEvent.GradleSyncQuickFix.SHOW_SYNC_ISSUES_DETAILS_HYPERLINK);
    myMessage = message;
    assert !details.isEmpty();
    myDetails = details;
  }

  @Override
  protected void execute(@NotNull Project project) {
    if (myDetailsDialog == null) {
      Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
      myDetailsDialog = new SyncIssueDetailsDialog(myMessage, myDetails, parentWindow);
    }
    myDetailsDialog.pack();
    myDetailsDialog.setModal(false);
    myDetailsDialog.setVisible(true);
  }

  @VisibleForTesting
  @NotNull
  public String getMessage() {
    return myMessage;
  }

  @VisibleForTesting
  @NotNull
  public List<String> getDetails() {
    return ImmutableList.copyOf(myDetails);
  }
}
