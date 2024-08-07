/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ui.problems;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;

/** Serialized state for the 'Blaze Problems' view. */
@State(
  name = "BlazeProblemsViewConfiguration",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
class ProblemsViewConfiguration implements PersistentStateComponent<ProblemsViewConfiguration> {

  private boolean autoscrollToConsole = false;

  public static ProblemsViewConfiguration getInstance(Project project) {
    return project.getService(ProblemsViewConfiguration.class);
  }

  public boolean getAutoscrollToConsole() {
    return autoscrollToConsole;
  }

  public void setAutoscrollToConsole(boolean autoscroll) {
    autoscrollToConsole = autoscroll;
  }

  @Override
  public ProblemsViewConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(final ProblemsViewConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
