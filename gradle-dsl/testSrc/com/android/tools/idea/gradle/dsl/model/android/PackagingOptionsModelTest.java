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
import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link PackagingOptionsModel}.
 */
public class PackagingOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsInApplicationStatements() throws Exception {
    writeToBuildFile(TestFile.PARSE_ELEMENTS_IN_APPLICATION_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("doNotStrip", ImmutableList.of("doNotStrip1", "doNotStrip2", "doNotStrip3"), packagingOptions.doNotStrip());
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  @Test
  public void testParseElementsInAssignmentStatements() throws Exception {
    writeToBuildFile(TestFile.PARSE_ELEMENTS_IN_ASSIGNMENT_STATEMENTS);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("doNotStrip", ImmutableList.of("doNotStrip1", "doNotStrip2", "doNotStrip3"), packagingOptions.doNotStrip());
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  @Test
  public void testReplaceElements() throws Exception {
    writeToBuildFile(TestFile.REPLACE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("doNotStrip", ImmutableList.of("doNotStrip1", "doNotStrip2", "doNotStrip3"), packagingOptions.doNotStrip());
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.doNotStrip().getListValue("doNotStrip3").setValue("doNotStripX");
    packagingOptions.excludes().getListValue("exclude1").setValue("excludeX");
    packagingOptions.merges().getListValue("merge2").setValue("mergeX");
    packagingOptions.pickFirsts().getListValue("pickFirst3").setValue("pickFirstX");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("doNotStrip", ImmutableList.of("doNotStrip1", "doNotStrip2", "doNotStripX"), packagingOptions.doNotStrip());
    assertEquals("excludes", ImmutableList.of("excludeX", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "mergeX", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirstX"), packagingOptions.pickFirsts());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TestFile.ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertMissingProperty("excludes", packagingOptions.excludes());
    assertMissingProperty("merges", packagingOptions.merges());
    assertMissingProperty("pickFirsts", packagingOptions.pickFirsts());

    packagingOptions.excludes().addListValue().setValue("exclude");
    packagingOptions.merges().addListValue().setValue("merge1");
    packagingOptions.merges().addListValue().setValue("merge2");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst1");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst2");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst3");

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    if(!isGroovy()) {
      verifyFileContents(myBuildFile, TestFile.ADD_ELEMENTS_EXPECTED);
    }
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  @Test
  public void testAppendElements() throws Exception {
    writeToBuildFile(TestFile.APPEND_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1"), packagingOptions.pickFirsts());

    packagingOptions.excludes().addListValue().setValue("exclude2");
    packagingOptions.merges().addListValue().setValue("merge2");
    packagingOptions.pickFirsts().addListValue().setValue("pickFirst2");

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    // verifyFileContents(myBuildFile, TestFile.APPEND_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2"), packagingOptions.pickFirsts());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    checkForValidPsiElement(packagingOptions, PackagingOptionsModelImpl.class);
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.excludes().delete();
    packagingOptions.merges().delete();
    packagingOptions.pickFirsts().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertMissingProperty("excludes", packagingOptions.excludes());
    assertMissingProperty("merges", packagingOptions.merges());
    assertMissingProperty("pickFirsts", packagingOptions.pickFirsts());
  }

  @Test
  public void testRemoveOneOfElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONE_OF_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.excludes().getListValue("exclude1").delete();
    packagingOptions.merges().getListValue("merge2").delete();
    packagingOptions.pickFirsts().getListValue("pickFirst3").delete();

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    // verifyFileContents(myBuildFile, TestFile.REMOVE_ONE_OF_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2"), packagingOptions.pickFirsts());
  }

  @Test
  public void testRemoveOnlyElement() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ONLY_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    checkForValidPsiElement(packagingOptions, PackagingOptionsModelImpl.class);
    assertEquals("excludes", ImmutableList.of("exclude"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst"), packagingOptions.pickFirsts());

    packagingOptions.excludes().getListValue("exclude").delete();
    packagingOptions.merges().getListValue("merge").delete();
    packagingOptions.pickFirsts().getListValue("pickFirst").delete();

    applyChangesAndReparse(buildModel);
    // TODO(b/144280051): we emit Dsl with syntax errors here
    // verifyFileContents(myBuildFile, TestFile.REMOVE_ONLY_ELEMENT_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertMissingProperty("excludes", packagingOptions.excludes());
    assertEmpty("merges", packagingOptions.merges().toList());
    assertEmpty("pickFirsts", packagingOptions.pickFirsts().toList());
  }

  @Test
  public void testAddPackagingFromEmpty800Beta01() throws IOException {
    writeToBuildFile("");

    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.getContext().setAgpVersion(AndroidGradlePluginVersion.Companion.parse("8.0.0-beta01"));
    buildModel.android().packaging().dex().useLegacyPackaging().setValue(true);
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PACKAGING_FROM_EMPTY_800_BETA_01);
  }

  @Test
  public void testAddPackagingFromEmpty800Beta02() throws IOException {
    writeToBuildFile("");

    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.getContext().setAgpVersion(AndroidGradlePluginVersion.Companion.parse("8.0.0-beta02"));
    buildModel.android().packaging().dex().useLegacyPackaging().setValue(true);
    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_PACKAGING_FROM_EMPTY_800_BETA_02);
  }

  enum TestFile implements TestFileName {
    PARSE_ELEMENTS_IN_APPLICATION_STATEMENTS("parseElementsInApplicationStatements"),
    PARSE_ELEMENTS_IN_ASSIGNMENT_STATEMENTS("parseElementsInAssignmentStatements"),
    REPLACE_ELEMENTS("replaceElements"),
    REPLACE_ELEMENTS_EXPECTED("replaceElementsExpected"),
    ADD_ELEMENTS("addElements"),
    ADD_ELEMENTS_EXPECTED("addElementsExpected"),
    ADD_PACKAGING_FROM_EMPTY_800_BETA_01("addPackagingFromEmpty800Beta01"),
    ADD_PACKAGING_FROM_EMPTY_800_BETA_02("addPackagingFromEmpty800Beta02"),
    APPEND_ELEMENTS("appendElements"),
    APPEND_ELEMENTS_EXPECTED("appendElementsExpected"),
    REMOVE_ELEMENTS("removeElements"),
    REMOVE_ONE_OF_ELEMENTS("removeOneOfElements"),
    REMOVE_ONE_OF_ELEMENTS_EXPECTED("removeOneOfElementsExpected"),
    REMOVE_ONLY_ELEMENT("removeOnlyElement"),
    REMOVE_ONLY_ELEMENT_EXPECTED("removeOnlyElementExpected"),
    ;
    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/packagingOptionsModel/" + path, extension);
    }
  }
}
