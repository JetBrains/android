/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/** Implementations of this EP will be notified when VCS sync events are detected. */
public interface VcsSyncListener {

  ExtensionPointName<VcsSyncListener> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.VcsSyncListener");

  /** Called when a VCS sync event is detected. */
  void onVcsSync(Project project);

  /**
   * Asynchronously notifies all available {@link VcsSyncListener}s of a VCS sync event, for all
   * open projects with the given VCS root.
   */
  static void notifyVcsSync(VirtualFile vcsRoot) {
    List<Project> projects =
        Arrays.stream(ProjectManager.getInstance().getOpenProjects())
            .filter(p -> ProjectLevelVcsManager.getInstance(p).getVcsFor(vcsRoot) != null)
            .collect(Collectors.toList());
    if (projects.isEmpty()) {
      return;
    }
    @SuppressWarnings("unused")
    Future<?> ignored =
        ApplicationManager.getApplication()
            .executeOnPooledThread(
                () ->
                    projects.forEach(
                        p -> {
                          for (VcsSyncListener l : EP_NAME.getExtensions()) {
                            l.onVcsSync(p);
                          }
                        }));
  }
}
