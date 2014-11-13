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

import com.android.tools.idea.gradle.service.sync.change.ProjectStructureChange;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for a service which knows how to flush ide changes of the gradle-backed project to the underlying <code>*.gradle</code> files.
 *
 * @param <T>  target project data type like module or library dependency etc (see {@link ProjectKeys})
 */
public interface Ide2GradleProjectSyncService<T> {

  @NotNull
  Key<T> getKey();

  /**
   * Asks to build an object representation of the project structure change given the previous and current state of
   * particular project entity.
   *
   * @param previousStateNode  previous state holder; <code>null</code> means project entity defined by the given 'current state' is added
   * @param currentStateNode   current state holder; <code>null</code> means that project entity defined by the given 'previous state'
   *                           is removed
   * @param project            current ide project
   * @return                   project change object considering given 'previous' and 'current' states;
   *                           <code>null</code> as an indication that there is no change or current service doesn't know how to propagate
   *                           such change to the underlying external config
   */
  @Nullable
  ProjectStructureChange<T> build(@Nullable DataNode<T> previousStateNode,
                                  @Nullable DataNode<T> currentStateNode,
                                  @NotNull Project project);

  /**
   * Asks to apply given change to the underlying external config files.
   *
   * @param change   change to apply
   * @param project  current ide project
   * @return         <code>true</code> if given change was successfully applied; <code>false</code> otherwise
   */
  boolean flush(@NotNull ProjectStructureChange<T> change, @NotNull Project project);
}
