/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An internal action to clear query sync dependencies, i.e. remove all dependencies from the
 * project.
 */
public final class ResetQuerySyncAction extends DumbAwareAction {
  private static final Logger logger = Logger.getInstance(ResetQuerySyncAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    QuerySyncManager qsm = QuerySyncManager.getInstance(e.getProject());
    qsm.resetQuerySyncState(
        QuerySyncActionStatsScope.create(getClass(), e), TaskOrigin.USER_ACTION);
  }

  /**
   * An implementation of {@link com.intellij.ide.caches.CachesInvalidator} for query sync.
   */
  public static class CachesInvalidator extends com.intellij.ide.caches.CachesInvalidator   {

    @Override
    public String getDescription() {
      return "Reset sync and code analysis state";
    }

    @Override
    public String getComment() {
      return "Completely resets the state of the project structure and disables code analysis in all files";
    }

    @Override
    public Boolean optionalCheckboxDefaultValue() {
      return true;
    }

    @Override
    public void invalidateCaches() {
      Arrays.stream(ProjectManager.getInstance().getOpenProjects())
        .flatMap(project -> {
          final var qsm = QuerySyncManager.getInstance(project);
          return qsm.getLoadedProject().stream();
        })
        .forEach(project -> {
          try {
            project.invalidateQuerySyncState(BlazeContext.create());
          }
          catch (BuildException e) {
            logger.error(e);
          }
        });
    }
  }
}
