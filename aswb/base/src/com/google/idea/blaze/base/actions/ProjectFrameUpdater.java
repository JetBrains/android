/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions;

import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;

/**
 * Workaround for https://youtrack.jetbrains.com/issue/IDEA-243994
 *
 * <p>Forces the entire project frame (including the main menubar) to update after a project is
 * opened. This ensures that even if the menubar's normal updater has been garbage-collected, {@link
 * BlazeMenuGroup#update} is called at least once after a project has opened.
 */
final class ProjectFrameUpdater implements ProjectManagerListener {

  BoolExperiment enabled = new BoolExperiment("blaze.menuBar.forceUpdate.onProjectOpen", true);

  @Override
  public void projectOpened(Project project) {
    if (!enabled.getValue()) {
      return;
    }
    ProjectFrameHelper frameHelper = WindowManagerEx.getInstanceEx().getFrameHelper(project);
    if (frameHelper != null) {
      frameHelper.updateView();
    }
  }
}
