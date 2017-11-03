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
package com.android.tools.idea.testartifacts.junit;

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.CompilerModuleExtension;

public class AndroidJUnitClasspathTest extends AndroidGradleTestCase {

  // See bug http://b.android.com/233410
  public void testRightPaths() throws Exception {
    loadSimpleApplication();
    Module module = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("app");
    Module[] modulesToCompile = {module};

    GradleInvocationResult invocationResult =
      invokeGradle(getProject(), invoker -> invoker.compileJava(modulesToCompile, TestCompileType.UNIT_TESTS));

    assertTrue(invocationResult.isBuildSuccessful());

    CompilerModuleExtension originalCompilerModuleExtension = CompilerModuleExtension.getInstance(module);
    assertNotNull(originalCompilerModuleExtension);
    assertFalse(originalCompilerModuleExtension.isCompilerOutputPathInherited());

    String compilerOutputUrlForTests = originalCompilerModuleExtension.getCompilerOutputUrlForTests();
    assertNotNull(compilerOutputUrlForTests);
    assertTrue(compilerOutputUrlForTests.contains("app/build/intermediates/classes/test/debug"));

    String compilerOutputUrl = originalCompilerModuleExtension.getCompilerOutputUrl();
    assertNotNull(compilerOutputUrl);
    assertTrue(compilerOutputUrl.contains("app/build/intermediates/classes/debug"));

    assertSize(2, originalCompilerModuleExtension.getOutputRootUrls(true));
  }
}
