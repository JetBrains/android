/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.compiler;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.util.BuildMode;
import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.compiler.impl.CompositeScope;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.compiler.AndroidGradleBuildTargetConstants.TARGET_ID;
import static com.android.tools.idea.gradle.compiler.AndroidGradleBuildTargetConstants.TARGET_TYPE_ID;
import static com.android.tools.idea.gradle.util.GradleProjects.getModulesToBuildFromSelection;
import static org.jetbrains.jps.api.CmdlineProtoUtil.createTargetsScope;

/**
 * Instructs the JPS builder to use Gradle to build the project.
 */
public class AndroidGradleBuildTargetScopeProvider extends BuildTargetScopeProvider {
  @Override
  @NotNull
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope,
                                                         @NotNull CompilerFilter filter,
                                                         @NotNull Project project,
                                                         boolean forceBuild) {
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle()) {
      return Collections.emptyList();
    }
    BuildSettings buildSettings = BuildSettings.getInstance(project);

    String runConfigurationTypeId = baseScope.getUserData(CompilerManager.RUN_CONFIGURATION_TYPE_ID_KEY);
    buildSettings.setRunConfigurationTypeId(runConfigurationTypeId);

    if (baseScope instanceof ProjectCompileScope) {
      // Make or Rebuild project
      BuildMode buildMode = forceBuild ? BuildMode.REBUILD : BuildMode.ASSEMBLE;
      if (buildSettings.getBuildMode() == null) {
        buildSettings.setBuildMode(buildMode);
      }
      Module[] modulesToBuild = ModuleManager.getInstance(project).getModules();
      buildSettings.setModulesToBuild(modulesToBuild);
    }
    else if (baseScope instanceof ModuleCompileScope) {
      String userDataString = ((ModuleCompileScope)baseScope).getUserDataString();
      Module[] modulesToBuild;
      if (userDataString.contains("RUN_CONFIGURATION")) {
        // Triggered by a "Run Configuration"
        modulesToBuild = baseScope.getAffectedModules();
      }
      else {
        // Triggered by menu item.
        // Make selected modules
        modulesToBuild = getModulesToBuildFromSelection(project, null);
      }
      buildSettings.setModulesToBuild(modulesToBuild);
      buildSettings.setBuildMode(BuildMode.ASSEMBLE);
    }
    else if (baseScope instanceof CompositeScope) {
      // Compile selected modules
      buildSettings.setModulesToBuild(getModulesToBuildFromSelection(project, null));
      buildSettings.setBuildMode(BuildMode.COMPILE_JAVA);
    }

    TargetTypeBuildScope scope = createTargetsScope(TARGET_TYPE_ID, Collections.singletonList(TARGET_ID), forceBuild);
    return Collections.singletonList(scope);
  }
}
