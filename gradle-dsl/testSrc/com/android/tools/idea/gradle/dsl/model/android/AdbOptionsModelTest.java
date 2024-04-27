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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.AdbOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.Test;

/**
 * Tests for {@link AdbOptionsModel}.
 */
public class AdbOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsOne() throws Exception {
    writeToBuildFile(TestFile.PARSE_ELEMENTS_ONE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(100), adbOptions.timeOutInMs());
  }

  @Test
  public void testParseElementsTwo() throws Exception {
    writeToBuildFile(TestFile.PARSE_ELEMENTS_TWO);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(200), adbOptions.timeOutInMs());
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TestFile.EDIT_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(200), adbOptions.timeOutInMs());

    adbOptions.installOptions().getListValue("efgh").setValue("xyz");
    adbOptions.timeOutInMs().setValue(300);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "xyz"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(300), adbOptions.timeOutInMs());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TestFile.ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertMissingProperty("installOptions", adbOptions.installOptions());

    adbOptions.installOptions().addListValue().setValue("abcd");
    adbOptions.timeOutInMs().setValue(100);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(100), adbOptions.timeOutInMs());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    checkForValidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(200), adbOptions.timeOutInMs());

    adbOptions.installOptions().delete();
    adbOptions.timeOutInMs().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    checkForInvalidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertMissingProperty("installOptions", adbOptions.installOptions());
    assertMissingProperty("timeOutInMs", adbOptions.timeOutInMs());
  }

  @Test
  public void testRemoveOneOfElementsInTheList() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());

    adbOptions.installOptions().getListValue("abcd").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("efgh"), adbOptions.installOptions());
  }

  @Test
  public void testRemoveOnlyElementInTheList() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONLY_ELEMENT_IN_THE_LIST);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    checkForValidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertEquals("installOptions", ImmutableList.of("abcd"), adbOptions.installOptions());

    adbOptions.installOptions().getListValue("abcd").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    checkForInvalidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertMissingProperty("installOptions", adbOptions.installOptions());
  }

  enum TestFile implements TestFileName {
    PARSE_ELEMENTS_ONE("parseElementsOne"),
    PARSE_ELEMENTS_TWO("parseElementsTwo"),
    EDIT_ELEMENTS("editElements"),
    EDIT_ELEMENTS_EXPECTED("editElementsExpected"),
    ADD_ELEMENTS("addElements"),
    ADD_ELEMENTS_EXPECTED("addElementsExpected"),
    REMOVE_ELEMENTS("removeElements"),
    REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST("removeOneOfElementsInTheList"),
    REMOVE_ONE_OF_ELEMENTS_IN_THE_LIST_EXPECTED("removeOneOfElementsInTheListExpected"),
    REMOVE_ONLY_ELEMENT_IN_THE_LIST("removeOnlyElementInTheList"),

    ;
    @NotNull @SystemIndependent private final String path;
    TestFile(@NotNull @SystemIndependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemIndependent String basePath, @NotNull String extension) {
      return new File(basePath + "/adbOptionsModel/" + path + extension);
    }
  }

}
