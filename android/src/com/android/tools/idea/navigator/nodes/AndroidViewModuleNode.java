// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.navigator.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * Specialization of {@link ProjectViewModuleNode} for Android view.
 */
public abstract class AndroidViewModuleNode extends ProjectViewModuleNode {
  public AndroidViewModuleNode(Project project, Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public boolean equals(Object o) {
    // All flavors of AndroidViewModuleNode representing the same module are considered equal (http://b/70635980).
    if (!(o instanceof AndroidViewModuleNode)) {
      return false;
    }
    return super.equals(o);
  }
}
