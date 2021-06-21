/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.api.android.TestCoverageModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;
import org.junit.Test;

/**
 * Tests for {@link TestCoverageModel}
 */
public class TestCoverageModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(TestFile.PARSE_ELEMENTS);
    AndroidModel android = getGradleBuildModel().android();
    TestCoverageModel testCoverage = android.testCoverage();
    assertEquals("jacocoVersion", "0.8.7", testCoverage.jacocoVersion());
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TestFile.EDIT_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    TestCoverageModel testCoverage = android.testCoverage();
    assertEquals("jacocoVersion", "0.8.5", testCoverage.jacocoVersion());

    testCoverage.jacocoVersion().setValue("0.8.7");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.EDIT_ELEMENTS_EXPECTED);
    assertEquals("jacocoVersion", "0.8.7", testCoverage.jacocoVersion());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TestFile.ADD_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    TestCoverageModel testCoverage = android.testCoverage();
    assertMissingProperty(testCoverage.jacocoVersion());

    testCoverage.jacocoVersion().setValue("0.8.7");
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_ELEMENTS_EXPECTED);
    assertEquals("jacocoVersion", "0.8.7", testCoverage.jacocoVersion());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_ELEMENTS);
    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    TestCoverageModel testCoverage = android.testCoverage();
    assertEquals("jacocoVersion", "0.8.7", testCoverage.jacocoVersion());

    testCoverage.jacocoVersion().delete();
    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");
    assertMissingProperty("jacocoVersion", testCoverage.jacocoVersion());
  }

  enum TestFile implements TestFileName {
    PARSE_ELEMENTS("parseElements"),
    EDIT_ELEMENTS("editElements"),
    EDIT_ELEMENTS_EXPECTED("editElementsExpected"),
    ADD_ELEMENTS("addElements"),
    ADD_ELEMENTS_EXPECTED("addElementsExpected"),
    REMOVE_ELEMENTS("removeElements"),
    ;
    @NotNull @SystemIndependent private final String path;

    TestFile(@NotNull @SystemIndependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemIndependent String basePath, @NotNull String extension) {
      return new File(basePath + "/testCoverageModel/" + path + extension);
    }
  }
}
