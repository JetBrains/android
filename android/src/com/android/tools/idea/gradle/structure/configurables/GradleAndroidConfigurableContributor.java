/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.configurables.messages.MessagesConfigurable;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.structure.dialog.AndroidConfigurableContributor;
import com.android.tools.idea.structure.dialog.ProjectStructureItemGroup;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GradleAndroidConfigurableContributor extends AndroidConfigurableContributor {
  private PsContext myContext;

  @Override
  @NotNull
  public List<Configurable> getMainConfigurables(@NotNull Project project, @NotNull Disposable parentDisposable) {
    myContext = new PsContext(new PsProject(project), parentDisposable);

    List<Configurable> configurables = Lists.newArrayList();
    configurables.add(new DependenciesPerspectiveConfigurable(myContext));

    return configurables;
  }

  @Override
  @NotNull
  public List<ProjectStructureItemGroup> getAdditionalConfigurableGroups() {
    assert myContext != null;
    ProjectStructureItemGroup messagesGroup = new ProjectStructureItemGroup("", new MessagesConfigurable(myContext));
    return com.google.android.collect.Lists.newArrayList(messagesGroup);
  }
}
