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
package com.android.tools.idea.gradle.service.sync.change;

import com.intellij.openapi.externalSystem.model.DataNode;
import org.jetbrains.annotations.NotNull;

public interface EntityModified<T> extends ProjectStructureChange<T> {

  @NotNull
  DataNode<T> getPreviousState();

  @NotNull
  DataNode<T> getCurrentState();

  /**
   * Most of project structure entities have compound state, e.g. a module/library dependency state consists of 'exported' and 'scope'
   * values.
   * <p/>
   * That means that we want to differentiate between particular sub-state change within the same project structure entity. An object
   * returned by the current method defines particular sub-state modification targeted by the current change object.
   *
   * @return    information about particular sub-state change type of the project structure entity targeted by the current change object
   */
  @NotNull
  Object getQualifier();
}
