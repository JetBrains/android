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
package com.android.tools.idea.testartifacts.scopes;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;

import static com.android.tools.idea.testing.TestProjectPaths.JAVA_LIB;
import static com.android.tools.idea.testing.TestProjectPaths.SYNC_MULTIPROJECT;
import static com.google.common.truth.Truth.assertThat;

public class AndroidJunitPatcherWithProjectsTest extends AndroidGradleTestCase {

  public void testRemoveAndroidTestClasspath() throws Exception {
    loadProject(SYNC_MULTIPROJECT);
    JUnitPatcher myPatcher = new AndroidJunitPatcher();

    Module module1 = ModuleManager.getInstance(myFixture.getProject()).findModuleByName("module1");
    JavaParameters parameters = new JavaParameters();
    parameters.configureByModule(module1, JavaParameters.CLASSES_AND_TESTS);

    String classpath = parameters.getClassPath().getPathsString();
    assertThat(classpath).contains("junit-4.12.jar");
    assertThat(classpath).contains("gson-2.2.4.jar");

    // JUnit is in test dependency, gson and guava are android test dependency
    myPatcher.patchJavaParameters(module1, parameters);
    classpath = parameters.getClassPath().getPathsString();
    assertThat(classpath).contains("junit-4.12.jar");
    assertThat(classpath).doesNotContain("gson-2.2.4.jar");
    assertThat(classpath).doesNotContain("guava-18.0.jar");
    assertThat(classpath).doesNotContain("module3");
  }

  public void testJavaLibDependencyResourcesInClasspath() throws Exception {
    loadProject(JAVA_LIB);
    testJavaLibResources("app");
    testKotlinClasses("app");
  }

  public void testJavaLibModuleResourcesInClasspath() throws Exception {
    loadProject(JAVA_LIB);
    testJavaLibResources("lib");
    testKotlinClasses("lib");
  }

  private void testJavaLibResources(@NotNull String moduleToTest) throws Exception {
    JUnitPatcher myPatcher = new AndroidJunitPatcher();

    Module module = ModuleManager.getInstance(getProject()).findModuleByName(moduleToTest);
    JavaParameters parameters = new JavaParameters();
    parameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS);

    File projectPath = getProjectFolderPath();
    String javaTestResources = new File(projectPath, "/lib/build/resources/test").toString();
    String javaMainResources = new File(projectPath, "/lib/build/resources/main").toString();

    String classpath = parameters.getClassPath().getPathsString();
    assertThat(classpath).doesNotContain(javaTestResources);
    assertThat(classpath).doesNotContain(javaMainResources);

    myPatcher.patchJavaParameters(module, parameters);
    classpath = parameters.getClassPath().getPathsString();

    assertThat(classpath).contains(javaTestResources);
    assertThat(classpath).contains(javaMainResources);
  }

  private void testKotlinClasses(@NotNull String moduleToTest) throws Exception {
    File projectPath = getProjectFolderPath();
    File kotlinMainClasses = new File(projectPath, "/lib/build/classes/kotlin/main");
    File kotlinTestClasses = new File(projectPath, "/lib/build/classes/kotlin/test");

    // Simulate Kotlin Gradle plugin 1.1.4 and Gradle 4.0
    Files.createDirectories(kotlinMainClasses.toPath());
    Files.createDirectories(kotlinTestClasses.toPath());

    Module module = ModuleManager.getInstance(getProject()).findModuleByName(moduleToTest);
    JavaParameters parameters = new JavaParameters();
    parameters.configureByModule(module, JavaParameters.CLASSES_AND_TESTS);

    String classpath = parameters.getClassPath().getPathsString();
    assertThat(classpath).doesNotContain(kotlinMainClasses.toString());
    assertThat(classpath).doesNotContain(kotlinTestClasses.toString());

    JUnitPatcher myPatcher = new AndroidJunitPatcher();
    myPatcher.patchJavaParameters(module, parameters);
    classpath = parameters.getClassPath().getPathsString();

    assertThat(classpath).contains(kotlinMainClasses.toString());
    assertThat(classpath).contains(kotlinTestClasses.toString());
  }
}
