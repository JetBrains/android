/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

import java.util.List;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.TestProjectPaths.*;

public class AndroidGradleBuildTargetProviderTest extends AndroidGradleTestCase {

  public void testNoAndroidFacetAddsScope() throws Exception {
    prepareProjectForImport(PURE_JAVA_PROJECT);
    Project project = getProject();
    importProject(project.getName(), getBaseDirPath(project), null);

    CompileScope scope = new ProjectCompileScope(getProject());

    AndroidGradleBuildTargetScopeProvider provider = new AndroidGradleBuildTargetScopeProvider();
    List<TargetTypeBuildScope> targetScopes = provider.getBuildTargetScopes(scope, (compiler) -> true, getProject(), true);
    assertSize(1, targetScopes);

    TargetTypeBuildScope targetScope = targetScopes.get(0);
    assertContainsElements(targetScope.getTargetIdList(), "android_gradle_build_target");
  }

  public void testProjectCompileScope() throws Exception {
    loadProject(SIMPLE_APPLICATION);

    CompileScope scope = new ProjectCompileScope(getProject());

    AndroidGradleBuildTargetScopeProvider provider = new AndroidGradleBuildTargetScopeProvider();
    List<TargetTypeBuildScope> targetScopes = provider.getBuildTargetScopes(scope, (compiler) -> true, getProject(), true);
    assertSize(1, targetScopes);

    TargetTypeBuildScope targetScope = targetScopes.get(0);
    assertContainsElements(targetScope.getTargetIdList(), "android_gradle_build_target");

    BuildSettings buildSettings = BuildSettings.getInstance(getProject());
    assertEquals(BuildMode.REBUILD, buildSettings.getBuildMode());

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSameElements(buildSettings.getModulesToBuild(), modules);
  }

  public void testModuleCompleScope() throws Exception {
    loadProject(MULTI_FEATURE);

    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertSize(6, modules);

    CompileScope scope = new ModuleCompileScope(modules[0], true);
    scope.putUserData(Key.create("Test"), "RUN_CONFIGURATION");

    AndroidGradleBuildTargetScopeProvider provider = new AndroidGradleBuildTargetScopeProvider();
    List<TargetTypeBuildScope> targetScopes = provider.getBuildTargetScopes(scope, (compiler) -> true, getProject(), true);
    assertSize(1, targetScopes);

    TargetTypeBuildScope targetScope = targetScopes.get(0);
    assertContainsElements(targetScope.getTargetIdList(), "android_gradle_build_target");

    BuildSettings buildSettings = BuildSettings.getInstance(getProject());
    assertEquals(BuildMode.ASSEMBLE, buildSettings.getBuildMode());

    Module[] expectedModule = {modules[0]};
    assertSameElements(buildSettings.getModulesToBuild(), expectedModule);
  }
}