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
package com.google.idea.blaze.python.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.python.PythonPluginUtils;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Set;

/**
 * Unlike most of the python-specific code, will be run even if the JetBrains python plugin isn't
 * enabled.
 */
public class AlwaysPresentPythonSyncPlugin implements BlazeSyncPlugin {

  @Override
  public ImmutableList<WorkspaceType> getSupportedWorkspaceTypes() {
    return ImmutableList.of(WorkspaceType.PYTHON);
  }

  @Override
  public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
    return ImmutableSet.of(LanguageClass.PYTHON);
  }

  @Override
  public ImmutableList<String> getRequiredExternalPluginIds(Collection<LanguageClass> languages) {
    String pluginId = PythonPluginUtils.getPythonPluginId();
    return pluginId != null && languages.contains(LanguageClass.PYTHON)
        ? ImmutableList.of(pluginId)
        : ImmutableList.of();
  }

  @Override
  public boolean validate(
      Project project, BlazeContext context, BlazeProjectData blazeProjectData) {
    String pluginId = PythonPluginUtils.getPythonPluginId();
    if (pluginId == null) {
      return true;
    }
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    if (importRoots == null) {
      return true;
    }
    boolean hasPythonTarget =
        blazeProjectData.getTargetMap().targets().stream()
            .filter(target -> importRoots.importAsSource(target.getKey().getLabel()))
            .anyMatch(target -> target.getKind().hasLanguage(LanguageClass.PYTHON));
    if (!hasPythonTarget) {
      return true;
    }
    if (!PluginUtils.isPluginEnabled(pluginId)) {
      IssueOutput.warn(
              "Your project appears to contain Python targets. To enable Python support, "
                  + "install/enable the JetBrains python plugin, then restart the IDE")
          .navigatable(PluginUtils.installOrEnablePluginNavigable(pluginId))
          .submit(context);
      return false;
    }
    return true;
  }
}
