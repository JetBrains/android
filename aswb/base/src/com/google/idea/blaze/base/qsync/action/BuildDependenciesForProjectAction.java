/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync.action;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.Nullable;

/**
 * Build all dependencies for the whole project and enables analysis for all targets.
 *
 * <p>Since this action may cause problems for large projects, warns the users and requests
 * confirmation if the project is large.
 */
public class BuildDependenciesForProjectAction extends BlazeProjectAction {

  private static final Logger logger = Logger.getInstance(BuildDependenciesForProjectAction.class);

  private static final int EXTERNAL_DEPS_WARNING_THRESHOLD = 10_000;

  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    QuerySyncManager syncMan = QuerySyncManager.getInstance(project);
    QuerySyncProjectSnapshot snapshot =
        syncMan
            .getLoadedProject()
            .flatMap(qsp -> qsp.getSnapshotHolder().getCurrent())
            .orElse(null);
    if (snapshot == null) {
      syncMan.notifyError("Project not loaded", "Cannot enable analysis before project is loaded.");
      return;
    }

    int externalDeps = snapshot.graph().projectDeps().size();
    logger.warn("Total external deps: " + externalDeps);
    if (externalDeps > EXTERNAL_DEPS_WARNING_THRESHOLD) {
      if (!new WarningDialog(project).showAndGet()) {
        return;
      }
    }

    QuerySyncActionStatsScope querySyncActionStats =
        QuerySyncActionStatsScope.create(this.getClass(), e);
    syncMan.enableAnalysisForWholeProject(querySyncActionStats, TaskOrigin.USER_ACTION);
  }

  static class WarningDialog extends DialogWrapper {

    WarningDialog(Project project) {
      super(project);
      init();
      setTitle("Project is Large");
      setModal(true);
    }

    @Override
    @Nullable
    protected JComponent createCenterPanel() {
      return new JLabel(
          "Your project is large. Enabling analysis for the whole project may fail,\n"
              + "be very slow, or make the IDE slow to use afterwards. Are you sure?");
    }

    @Override
    protected Action[] createActions() {
      return new Action[] {getOKAction(), getCancelAction()};
    }
  }
}
