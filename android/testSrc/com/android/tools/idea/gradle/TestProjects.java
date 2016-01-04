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

import com.android.tools.idea.gradle.stubs.android.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Factory of {@link com.android.builder.model.AndroidProject}s and {@link com.android.builder.model.NativeAndroidProject}s for testing
 * purposes. The created projects mimic the structure of the sample projects distributed with the Android Gradle plug-in.
 */
public final class TestProjects {
  private static final String BASIC_PROJECT_NAME = "basic";
  private static final String NATIVE_PROJECT_NAME = "name";

  private TestProjects() {
  }

  @NotNull
  public static AndroidProjectStub createBasicProject() {
    AndroidProjectStub androidProject = new AndroidProjectStub(BASIC_PROJECT_NAME);
    createBasicProject(androidProject);
    return androidProject;
  }

  @NotNull
  public static AndroidProjectStub createBasicProject(@NotNull File parentDir) {
    return createBasicProject(parentDir, BASIC_PROJECT_NAME);
  }

  @NotNull
  public static AndroidProjectStub createBasicProject(@NotNull File parentDir, @NotNull String name) {
    AndroidProjectStub androidProject = new AndroidProjectStub(parentDir, name);
    createBasicProject(androidProject);
    return androidProject;
  }

  private static void createBasicProject(@NotNull AndroidProjectStub androidProject) {
    androidProject.addBuildType("debug");
    VariantStub debugVariant = androidProject.addVariant("debug");

    AndroidArtifactStub mainArtifactInfo = debugVariant.getMainArtifact();
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/debug");
    mainArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/debug");

    AndroidArtifactStub testArtifactInfo = debugVariant.getInstrumentTestArtifact();
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/test");
    testArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/test");
  }

  @NotNull
  public static AndroidProjectStub createFlavorsProject() {
    AndroidProjectStub project = new AndroidProjectStub("flavors");

    project.addBuildType("debug");
    VariantStub f1faDebugVariant = project.addVariant("f1fa-debug", "debug");

    AndroidArtifactStub mainArtifactInfo = f1faDebugVariant.getMainArtifact();
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/f1fa/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/f1fa/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/f1fa/debug");
    mainArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/f1fa/debug");
    mainArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/f1fa/debug");

    AndroidArtifactStub testArtifactInfo = f1faDebugVariant.getInstrumentTestArtifact();
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/aidl/f1fa/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/buildConfig/f1fa/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/r/f1fa/test");
    testArtifactInfo.addGeneratedSourceFolder("build/generated/source/rs/f1fa/test");
    testArtifactInfo.addGeneratedResourceFolder("build/generated/res/rs/f1fa/test");

    f1faDebugVariant.addProductFlavors("f1", "fa");

    project.addProductFlavor("f1");
    project.addProductFlavor("fa");

    return project;
  }

  @NotNull
  public static NativeAndroidProjectStub createNativeProject() {
    NativeAndroidProjectStub nativeAndroidProject = new NativeAndroidProjectStub(NATIVE_PROJECT_NAME);
    createNativeProject(nativeAndroidProject);
    return nativeAndroidProject;
  }

  @NotNull
  public static NativeAndroidProjectStub createNativeProject(@NotNull File parentDir) {
    return createNativeProject(parentDir, NATIVE_PROJECT_NAME);
  }

  @NotNull
  public static NativeAndroidProjectStub createNativeProject(@NotNull File parentDir, @NotNull String name) {
    NativeAndroidProjectStub nativeAndroidProject = new NativeAndroidProjectStub(parentDir, name);
    createNativeProject(nativeAndroidProject);
    return nativeAndroidProject;
  }

  private static void createNativeProject(@NotNull NativeAndroidProjectStub nativeAndroidProjectStub) {
    NativeArtifactStub artifactStub = nativeAndroidProjectStub.addNativeArtifact("lib");
    artifactStub.addExportedHeaders("src/main/headers/");
    artifactStub.addSourceFolder("src/main/xyz/");
    artifactStub.addSourceFile("src/main/abc/foo.c");
  }
}
