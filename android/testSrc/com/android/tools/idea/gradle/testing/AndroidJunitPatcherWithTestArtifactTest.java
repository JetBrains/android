/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.testing;

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;

import static com.google.common.truth.Truth.assertThat;

public class AndroidJunitPatcherWithTestArtifactTest extends AndroidGradleTestCase {

  public void testRemoveAndroidTestClasspath() throws Exception {
    loadProject("projects/sync/multiproject", false);
    JUnitPatcher myPatcher = new AndroidJunitPatcher();

    Module module1 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("module1");
    JavaParameters parameters = new JavaParameters();
    parameters.configureByModule(module1, JavaParameters.CLASSES_AND_TESTS);

    String classpath = parameters.getClassPath().getPathsString();
    assertThat(classpath).contains("junit-4.12.jar");
    assertThat(classpath).contains("gson-2.4.jar");
    assertThat(classpath).contains("guava-18.0.jar");

    // JUnit is in test dependency, gson and guava are android test dependency
    myPatcher.patchJavaParameters(module1, parameters);
    classpath = parameters.getClassPath().getPathsString();
    assertThat(classpath).contains("junit-4.12.jar");
    assertThat(classpath).doesNotContain("gson-2.4.jar");
    assertThat(classpath).doesNotContain("guava-18.0.jar");
    assertThat(classpath).doesNotContain("module3");
  }
}
