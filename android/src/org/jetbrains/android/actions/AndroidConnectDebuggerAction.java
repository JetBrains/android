// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.actions;

import static com.android.tools.idea.run.debug.UtilsKt.showError;

import com.android.annotations.concurrency.Slow;
import com.android.ddmlib.Client;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.RunConfigurationWithDebugger;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        () -> closeOldSessionAndRun(project, dialog.getSelectedAndroidDebugger(), client, dialog.getRunConfiguration()));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    boolean isVisible = isAndroidStudio ||
                        (project != null && ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID));
    e.getPresentation().setVisible(isVisible);
  }

  @Slow
  public static void closeOldSessionAndRun(@NotNull Project project,
                                            @NotNull AndroidDebugger androidDebugger,
                                            @NotNull Client client,
                                            @Nullable RunConfigurationWithDebugger configuration) {
    terminateRunSessions(project, client);
    AndroidDebuggerState state;
    if (configuration != null) {
      state = configuration.getAndroidDebuggerContext().getAndroidDebuggerState();
    }
    else {
      state = androidDebugger.createState();
    }
    androidDebugger.attachToClient(project, client, state)
      .onError(e -> {
        if (e instanceof ExecutionException) {
          showError(project, (ExecutionException)e, "Attach debug to process");
        }
        else {
          Logger.getInstance(AndroidConnectDebuggerAction.class).error(e);
        }
      });
  }

  // Disconnect any active run sessions to the same client
  private static void terminateRunSessions(@NotNull Project project, @NotNull Client selectedClient) {
    int pid = selectedClient.getClientData().getPid();

    // find if there are any active run sessions to the same client, and terminate them if so
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (handler instanceof AndroidProcessHandler) {
        Client client = ((AndroidProcessHandler)handler).getClient(selectedClient.getDevice());
        if (client != null && client.getClientData().getPid() == pid) {
          handler.notifyTextAvailable("Disconnecting run session: a new debug session will be established.\n", ProcessOutputTypes.STDOUT);
          handler.detachProcess();
          break;
        }
      }
    }
  }
}
