package com.google.idea.blaze.base.qsync.action;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

/**
 * An internal action to purge the build artifact cache, i.e. remove all entries from it. This does
 * not directly affect the project structure.
 */
public class PurgeBuildCacheAction extends BlazeProjectAction implements DumbAware {

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.REQUIRED;
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    QuerySyncManager.getInstance(project)
        .purgeBuildCache(QuerySyncActionStatsScope.create(getClass(), e));
  }
}
