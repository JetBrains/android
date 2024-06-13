// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.execution.common.debug.utils.AndroidConnectDebugger;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

public class AndroidConnectDebuggerAction extends AnAction {
  private final boolean isAndroidStudio = IdeInfo.getInstance().isAndroidStudio();

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    assert project != null;

    final AndroidProcessChooserDialog dialog = new AndroidProcessChooserDialog(project, true);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      Client client = dialog.getClient();
      if (client == null) {
        return;
      }

      AppExecutorUtil.getAppExecutorService().execute(
        () -> AndroidConnectDebugger.closeOldSessionAndRun(project, dialog.getSelectedAndroidDebugger(), client, dialog.getRunConfiguration()));
    }
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    boolean isVisible = isAndroidStudio ||
                        (project != null && CommonAndroidUtil.getInstance().isAndroidProject(project));
    e.getPresentation().setVisible(isVisible);
  }
}
