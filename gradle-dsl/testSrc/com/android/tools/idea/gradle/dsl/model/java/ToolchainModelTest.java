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
package com.android.tools.idea.gradle.dsl.model.java;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.java.ToolchainModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

public class ToolchainModelTest extends GradleFileModelTestCase {

  @Test
  public void addToolchain() throws IOException {
    writeToBuildFile(TestFile.ADD_TOOLCHAIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();
    assertMissingProperty(toolchain.languageVersion());

    toolchain.languageVersion().setVersion(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_TOOLCHAIN_EXPECTED);
    checkForValidPsiElement(getGradleBuildModel().java().toolchain(), ToolchainModelImpl.class);
    assertEquals("languageVersion", Integer.valueOf(17), toolchain.languageVersion().version());
  }

  @Test
  public void removeToolchain() throws IOException {
    writeToBuildFile(TestFile.REMOVE_TOOLCHAIN);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();

    toolchain.delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_TOOLCHAIN_EXPECTED);
    toolchain = getGradleBuildModel().java().toolchain();
    checkForInvalidPsiElement(toolchain, ToolchainModelImpl.class);
    assertMissingProperty(toolchain.languageVersion());
  }

  @Test
  public void updateToolchain() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_INT);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();
    assertEquals(Integer.valueOf(21), toolchain.languageVersion().version());

    toolchain.languageVersion().setVersion(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_EXPECTED);

    toolchain = getGradleBuildModel().java().toolchain();
    assertEquals(Integer.valueOf(17), toolchain.languageVersion().version());
  }

  @Test
  public void updateToolchainFromString() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_STRING);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();
    assertEquals(Integer.valueOf(21), toolchain.languageVersion().version());

    toolchain.languageVersion().setVersion(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_EXPECTED);

    toolchain = getGradleBuildModel().java().toolchain();
    assertEquals(Integer.valueOf(17), toolchain.languageVersion().version());
  }

  @Test
  public void updateToolchainFromInvalidString() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_INVALID_STRING);
    checkReadInvalidAndSetTo17();
  }

  @Test
  public void updateToolchainFromInvalidValue_null() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_INVALID_VALUE_NULL);
    checkReadInvalidAndSetTo17();
  }

  @Test
  public void updateToolchainFromNoArgument() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_NO_ARGUMENT);
    checkReadInvalidAndSetTo17();
  }

  @Test
  public void updateToolchainFromNullSet() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_NULL);
    checkReadInvalidAndSetTo17();
  }

  private void checkReadInvalidAndSetTo17() throws IOException {
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();
    assertNull(toolchain.languageVersion().version());

    toolchain.languageVersion().setVersion(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_EXPECTED);

    toolchain = getGradleBuildModel().java().toolchain();
    assertEquals(Integer.valueOf(17), toolchain.languageVersion().version());
  }

  @Test
  public void updateToolchainFromArgumentAsReference() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_ARGUMENT_AS_REFERENCE);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();
    assertEquals(Integer.valueOf(21), toolchain.languageVersion().version());

    toolchain.languageVersion().setVersion(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_VERSION_ARGUMENT_AS_REFERENCE_EXPECTED);

    toolchain = getGradleBuildModel().java().toolchain();
    assertEquals(Integer.valueOf(17), toolchain.languageVersion().version());
  }

  @Test
  public void updateToolchainFromValueAsReference() throws IOException {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_VALUE_AS_REFERENCE);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    ToolchainModel toolchain = java.toolchain();
    assertEquals(Integer.valueOf(21), toolchain.languageVersion().version());

    toolchain.languageVersion().setVersion(17);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_VERSION_VALUE_AS_REFERENCE_EXPECTED);

    toolchain = getGradleBuildModel().java().toolchain();
    assertEquals(Integer.valueOf(17), toolchain.languageVersion().version());
  }

  enum TestFile implements TestFileName {
    READ_TOOLCHAIN_VERSION_INT("readToolchainVersionDefinedAsInt"),
    READ_TOOLCHAIN_VERSION_STRING("readToolchainVersionDefinedAsString"),
    READ_TOOLCHAIN_VERSION_INVALID_STRING("readToolchainVersionInvalidString"),
    READ_TOOLCHAIN_VERSION_INVALID_VALUE_NULL("readToolchainVersionInvalidValueNull"),
    READ_TOOLCHAIN_VERSION_NO_ARGUMENT("readToolchainVersionNoArgument"),
    READ_TOOLCHAIN_VERSION_NULL("readToolchainVersionNull"),
    READ_TOOLCHAIN_VERSION_VALUE_AS_REFERENCE("readToolchainVersionReference"),
    UPDATE_TOOLCHAIN_VERSION_VALUE_AS_REFERENCE_EXPECTED("updateToolchainVersionReferenceExpected"),
    READ_TOOLCHAIN_VERSION_ARGUMENT_AS_REFERENCE("readToolchainVersionArgumentReference"),
    UPDATE_TOOLCHAIN_VERSION_ARGUMENT_AS_REFERENCE_EXPECTED("updateToolchainVersionArgumentReferenceExpected"),
    ADD_TOOLCHAIN("addToolchain"),
    ADD_TOOLCHAIN_EXPECTED("addToolchainExpected"),
    REMOVE_TOOLCHAIN("removeToolchain"),
    REMOVE_TOOLCHAIN_EXPECTED("removeToolchainExpected"),
    UPDATE_TOOLCHAIN_EXPECTED("updateToolchainExpected"),
    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/javaToolchainModel/" + path, extension);
    }
  }
}
