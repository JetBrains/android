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
package com.android.tools.idea.gradle.compiler;

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

import java.util.Collections;
import java.util.List;

/**
 * Instructs the JPS builder to use Gradle to build the project.
 */
public class AndroidGradleBuildTargetScopeProvider extends BuildTargetScopeProvider {
  public static final String TARGET_ID = "android_gradle_build_target";
  public static final String TARGET_TYPE_ID = "android_gradle_build_target_type";

  @Override
  @NotNull
  public List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope,
                                                         @NotNull CompilerFilter filter,
                                                         @NotNull Project project,
                                                         boolean forceBuild) {
    if (!Projects.isGradleProject(project)) {
      return Collections.emptyList();
    }
    TargetTypeBuildScope scope = CmdlineProtoUtil.createTargetsScope(TARGET_TYPE_ID, Collections.singletonList(TARGET_ID), forceBuild);
    return Collections.singletonList(scope);
  }
}
