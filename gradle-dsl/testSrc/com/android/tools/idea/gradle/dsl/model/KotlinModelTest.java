/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinModel;
import com.android.tools.idea.gradle.dsl.model.kotlin.KotlinModelImpl;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

public class KotlinModelTest extends GradleFileModelTestCase {

  @Test
  public void addToolchain() throws IOException {
    writeToBuildFile(TestFile.ADD_TOOLCHAIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    KotlinModel kotlin = buildModel.kotlin();
    assertMissingProperty(kotlin.jvmToolchain());

    kotlin.jvmToolchain().setValue(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_TOOLCHAIN_EXPECTED);
    checkForValidPsiElement(getGradleBuildModel().kotlin(), KotlinModelImpl.class);
    assertEquals("jvmToolchain", Integer.valueOf(17), kotlin.jvmToolchain().toInt());
  }

  @Test
  public void removeToolchain() throws IOException {
    writeToBuildFile(TestFile.REMOVE_TOOLCHAIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    KotlinModel kotlin = buildModel.kotlin();
    kotlin.jvmToolchain().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_TOOLCHAIN_EXPECTED);
    kotlin = getGradleBuildModel().kotlin();
    checkForInvalidPsiElement(kotlin, KotlinModelImpl.class);
    assertMissingProperty(kotlin.jvmToolchain());
  }

  @Test
  public void updateToolchain() throws IOException {
    writeToBuildFile(TestFile.REMOVE_TOOLCHAIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    KotlinModel kotlin = buildModel.kotlin();
    assertEquals(Integer.valueOf(17), kotlin.jvmToolchain().toInt());

    kotlin.jvmToolchain().setValue(21);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_EXPECTED);

    kotlin = getGradleBuildModel().kotlin();
    assertEquals(Integer.valueOf(21), kotlin.jvmToolchain().toInt());
  }

  @Test
  public void readToolchainVersionAsReference() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_AS_REFERENCE);
    GradleBuildModel buildModel = getGradleBuildModel();
    KotlinModel kotlin = buildModel.kotlin();
    assertEquals(Integer.valueOf(21), kotlin.jvmToolchain().toInt());
  }


  enum TestFile implements TestFileName {
    ADD_TOOLCHAIN("addToolchain"),
    ADD_TOOLCHAIN_EXPECTED("addToolchainExpected"),
    REMOVE_TOOLCHAIN("removeToolchain"),
    REMOVE_TOOLCHAIN_EXPECTED("removeToolchainExpected"),
    UPDATE_TOOLCHAIN_EXPECTED("updateToolchainExpected"),
    READ_TOOLCHAIN_VERSION_AS_REFERENCE("readToolchainVersionArgumentReference")
    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/kotlinModel/" + path, extension);
    }
  }
}
