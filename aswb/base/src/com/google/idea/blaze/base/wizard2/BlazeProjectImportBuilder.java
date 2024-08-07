/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.wizard2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import java.util.List;
import javax.annotation.Nullable;

/** Wrapper around a {@link BlazeNewProjectBuilder} to fit into IntelliJ's import framework. */
@VisibleForTesting
public class BlazeProjectImportBuilder extends ProjectBuilder {
  private BlazeNewProjectBuilder builder = new BlazeNewProjectBuilder();

  @Nullable
  @Override
  public List<Module> commit(
      Project project,
      @Nullable ModifiableModuleModel modifiableModuleModel,
      ModulesProvider modulesProvider) {
    builder.commitToProject(project);
    return ImmutableList.of();
  }

  public BlazeNewProjectBuilder builder() {
    return builder;
  }
}
