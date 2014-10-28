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
package com.android.tools.idea.gradle.service.sync.service;

import com.android.tools.idea.gradle.service.sync.change.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractIde2GradleProjectSyncService<T> implements Ide2GradleProjectSyncService<T>, ProjectStructureChangeVisitor {

  private static final Logger LOG = Logger.getInstance(AbstractIde2GradleProjectSyncService.class);

  private final ThreadLocal<Context> myContext = new ThreadLocal<Context>();

  @Override
  public boolean flush(@NotNull ProjectStructureChange<T> change, @NotNull Project project) {
    Context context = new Context(project);
    myContext.set(context);
    try {
      change.accept(this);
      return context.success;
    }
    finally {
      // Ensure we don't hold Project object here.
      myContext.set(null);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void visit(@NotNull EntityRemoved<?> change) {
    myContext.get().success = processEntityRemoval((EntityRemoved<T>)change, myContext.get().ideProject);
  }

  @SuppressWarnings("UnusedParameters")
  protected boolean processEntityRemoval(@NotNull EntityRemoved<T> change, @NotNull Project ideProject) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Change '%s' is not supported by ide -> gradle sync service %s", change, getClass()));
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void visit(@NotNull EntityAdded<?> change) {
    myContext.get().success = processEntityAddition((EntityAdded<T>)change, myContext.get().ideProject);
  }

  protected boolean processEntityAddition(@NotNull EntityAdded<T> change, @NotNull Project ideProject) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Change '%s' is not supported by ide -> gradle sync service %s", change, getClass()));
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void visit(@NotNull EntityModified<?> change) {
    myContext.get().success = processEntityModification((EntityModified<T>)change, myContext.get().ideProject);
  }

  @SuppressWarnings("UnusedParameters")
  protected boolean processEntityModification(@NotNull EntityModified<T> change, @NotNull Project ideProject) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Change '%s' is not supported by ide -> gradle sync service %s", change, getClass()));
    }
    return false;
  }

  private static class Context {
    @NotNull final Project ideProject;
    boolean success;

    Context(@NotNull Project ideProject) {
      this.ideProject = ideProject;
    }
  }
}
