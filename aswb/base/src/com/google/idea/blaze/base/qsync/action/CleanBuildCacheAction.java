package com.google.idea.blaze.base.qsync.action;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * An internal action to clean the build artifact cache. This does not directly affect the project
 * structure.
 */
public class CleanBuildCacheAction extends BlazeProjectAction implements DumbAware {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    QuerySyncManager.getInstance(project).cleanCacheNow();
  }
}
