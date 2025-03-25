package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.Nullable;

public interface ProjectLoader {
  @Nullable QuerySyncProject loadProject(BlazeContext context) throws BuildException;

  ModificationTracker getProjectModificationTracker();
}
