/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

public class AndroidConnectDebuggerAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    assert project != null;

    if (!AndroidSdkUtils.activateDdmsIfNecessary(project)) {
      return;
    }

    final AndroidProcessChooserDialog dialog = new AndroidProcessChooserDialog(project, true);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      Client client = dialog.getClient();
      if (client == null) {
        return;
      }

      closeOldSessionAndRun(project, dialog.getAndroidDebugger(), client);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getProject();
    e.getPresentation().setVisible(project != null &&
                                   !ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).isEmpty());
  }

  private static void closeOldSessionAndRun(@NotNull Project project, @NotNull AndroidDebugger androidDebugger, @NotNull Client client) {
    terminateRunSessions(project, client);
    androidDebugger.attachToClient(project, client);
  }

  // Disconnect any active run sessions to the same client
  private static void terminateRunSessions(@NotNull Project project, @NotNull Client selectedClient) {
    int pid = selectedClient.getClientData().getPid();

    // find if there are any active run sessions to the same client, and terminate them if so
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      if (handler instanceof AndroidProcessHandler) {
        Client client = ((AndroidProcessHandler)handler).getClient(selectedClient.getDevice());
        if (client != null && client.getClientData().getPid() == pid) {
          ((AndroidProcessHandler)handler).setNoKill();
          handler.detachProcess();
          handler.notifyTextAvailable("Disconnecting run session: a new debug session will be established.\n", ProcessOutputTypes.STDOUT);
          break;
        }
      }
    }
  }
}
