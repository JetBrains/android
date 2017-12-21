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
package com.android.tools.idea.gradle;

import com.android.tools.idea.gradle.stubs.android.TestAndroidArtifact;
import com.android.tools.idea.gradle.stubs.android.TestAndroidProject;
import com.android.tools.idea.gradle.stubs.android.TestVariant;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Factory of {@link com.android.builder.model.AndroidProject}s and {@link com.android.builder.model.NativeAndroidProject}s for testing
 * purposes. The created projects mimic the structure of the sample projects distributed with the Android Gradle plug-in.
 */
public final class TestProjects {
  private static final String BASIC_PROJECT_NAME = "basic";

  private TestProjects() {
  }

  @NotNull
  public static TestAndroidProject createBasicProject() {
    TestAndroidProject androidProject = new TestAndroidProject(BASIC_PROJECT_NAME);
    createBasicProject(androidProject);
    return androidProject;
  }

  @NotNull
  public static TestAndroidProject createBasicProject(@NotNull File parentFolderPath) {
    return createBasicProject(parentFolderPath, BASIC_PROJECT_NAME);
  }

  @NotNull
  public static TestAndroidProject createBasicProject(@NotNull File parentFolderPath, @NotNull String name) {
    TestAndroidProject androidProject = new TestAndroidProject(parentFolderPath, name);
    createBasicProject(androidProject);
    return androidProject;
  }

  private static void createBasicProject(@NotNull TestAndroidProject androidProject) {
    androidProject.addBuildType("debug");
    TestVariant debugVariant = androidProject.addVariant("debug");

    TestAndroidArtifact mainArtifactInfo = debugVariant.getMainArtifact();
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/debug");
    mainArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/debug");

    TestAndroidArtifact testArtifactInfo = debugVariant.getInstrumentTestArtifact();
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/test");
    testArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/test");
  }

  @NotNull
  public static TestAndroidProject createFlavorsProject() {
    TestAndroidProject androidProject = new TestAndroidProject("flavors");

    androidProject.addBuildType("debug");
    TestVariant f1faDebugVariant = androidProject.addVariant("f1fa-debug", "debug");

    TestAndroidArtifact mainArtifactInfo = f1faDebugVariant.getMainArtifact();
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/f1fa/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/f1fa/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/f1fa/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/f1fa/debug");
    mainArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/f1fa/debug");

    TestAndroidArtifact testArtifactInfo = f1faDebugVariant.getInstrumentTestArtifact();
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/f1fa/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/f1fa/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/f1fa/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/f1fa/test");
    testArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/f1fa/test");

    f1faDebugVariant.addProductFlavors("f1", "fa");

    androidProject.addProductFlavor("f1", "dim1");
    androidProject.addProductFlavor("fa", "dim2");

    return androidProject;
  }
}
