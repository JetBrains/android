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

import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Factory of {@link com.android.build.gradle.model.AndroidProject}s for testing purposes. The created projects mimic the structure of the
 * sample projects distributed with the Android Gradle plug-in.
 */
public final class TestProjects {
  private static final String BASIC_PROJECT_NAME = "basic";

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

    debugVariant.addGeneratedSourceFolder("build/source/aidl/debug");
    debugVariant.addGeneratedSourceFolder("build/source/buildConfig/debug");
    debugVariant.addGeneratedSourceFolder("build/source/r/debug");
    debugVariant.addGeneratedSourceFolder("build/source/rs/debug");

    debugVariant.addGeneratedResourceFolder("build/res/rs/debug");

    debugVariant.addGeneratedTestSourceFolder("build/source/aidl/test");
    debugVariant.addGeneratedTestSourceFolder("build/source/buildConfig/test");
    debugVariant.addGeneratedTestSourceFolder("build/source/r/test");
    debugVariant.addGeneratedTestSourceFolder("build/source/rs/test");

    debugVariant.addGeneratedTestResourceFolder("build/res/rs/test");
  }

  @NotNull
  public static AndroidProjectStub createFlavorsProject() {
    AndroidProjectStub project = new AndroidProjectStub("flavors");

    project.addBuildType("debug");
    VariantStub f1faDebugVariant = project.addVariant("f1fa-debug", "debug");

    f1faDebugVariant.addGeneratedSourceFolder("build/source/aidl/f1fa/debug");
    f1faDebugVariant.addGeneratedSourceFolder("build/source/buildConfig/f1fa/debug");
    f1faDebugVariant.addGeneratedSourceFolder("build/source/r/f1fa/debug");
    f1faDebugVariant.addGeneratedSourceFolder("build/source/rs/f1fa/debug");

    f1faDebugVariant.addGeneratedResourceFolder("build/res/rs/f1fa/debug");

    f1faDebugVariant.addGeneratedTestSourceFolder("build/source/aidl/f1fa/test");
    f1faDebugVariant.addGeneratedTestSourceFolder("build/source/buildConfig/f1fa/test");
    f1faDebugVariant.addGeneratedTestSourceFolder("build/source/r/f1fa/test");
    f1faDebugVariant.addGeneratedTestSourceFolder("build/source/rs/f1fa/test");

    f1faDebugVariant.addGeneratedTestResourceFolder("build/res/rs/f1fa/test");

    f1faDebugVariant.addProductFlavors("f1", "fa");

    project.addProductFlavor("f1");
    project.addProductFlavor("fa");

    return project;
  }
}
