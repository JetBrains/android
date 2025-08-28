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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_SUITE_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_SUITE_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_SUITE_MODEL_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_SUITE_MODEL_PARSE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.model.android.AndroidModelUtilsKt.android;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.TestSuiteModel;
import com.android.tools.idea.gradle.dsl.api.android.testOptions.testSuites.TargetModel;
import com.android.tools.idea.gradle.dsl.api.util.DeletablePsiElementHolder;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import java.util.List;
import org.junit.Test;

/**
 * Tests for {@link TestSuiteModel}.
 */
public class TestSuiteModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(TEST_SUITE_MODEL_PARSE_ELEMENTS);

    AndroidModel android = android(getGradleBuildModel());
    assertNotNull(android);

    TestSuiteModel testSuiteModel = android.testOptions().suites().get(0);
    assertNotNull(testSuiteModel);
    assertEquals("name", "testSuite", testSuiteModel.name());
    assertEquals("targetVariants", List.of("debug", "release"), testSuiteModel.targetVariants());
    assertNotNull(testSuiteModel.assets());
    assertEquals("targets", List.of("default"), testSuiteModel.targets().stream().map(TargetModel::name).toList());
    assertEquals("useJunitEngine.inputs", List.of("com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS"),
                 testSuiteModel.useJunitEngine().inputs());
    assertEquals("useJunitEngine.includeEngines", List.of("test-engine-id"), testSuiteModel.useJunitEngine().includeEngines());
    assertEquals("useJunitEngine.enginesDependencies",
                 List.of("org.junit.platform:junit-platform-launcher",
                         "org.junit.platform:junit-platform-engine:1.12.0"), testSuiteModel.useJunitEngine().enginesDependencies());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TEST_SUITE_MODEL_ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = android(buildModel);
    assertNotNull(android);

    TestSuiteModel testSuite = android.testOptions().suites().get(0);
    testSuite.addTargetVariant("debug");
    testSuite.addTarget("default");
    testSuite.addAssets();
    testSuite.useJunitEngine().addInput("com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS");
    testSuite.useJunitEngine().addIncludeEngine("test-engine-id");
    testSuite.useJunitEngine().addEngineDependency("org.junit.platform:junit-platform-launcher");
    testSuite.useJunitEngine().addEngineDependency("org.junit.platform:junit-platform-engine:1.12.0");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TEST_SUITE_MODEL_ADD_ELEMENTS_EXPECTED);

    TestSuiteModel testSuiteModel = android.testOptions().suites().get(0);
    assertNotNull(testSuiteModel);
    assertEquals("name", "testSuite", testSuiteModel.name());
    assertEquals("targetVariants", List.of("debug"), testSuiteModel.targetVariants());
    assertNotNull(testSuiteModel.assets());
    assertEquals("targets", List.of("default"), testSuiteModel.targets().stream().map(TargetModel::name).toList());

    assertEquals("useJunitEngine.inputs", List.of("com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS"),
                 testSuiteModel.useJunitEngine().inputs());
    assertEquals("useJunitEngine.includeEngines", List.of("test-engine-id"), testSuiteModel.useJunitEngine().includeEngines());
    assertEquals("useJunitEngine.enginesDependencies",
                 List.of("org.junit.platform:junit-platform-launcher",
                         "org.junit.platform:junit-platform-engine:1.12.0"), testSuiteModel.useJunitEngine().enginesDependencies());
  }

  @Test
  public void testDuplicatesAreNotAdded() throws Exception {
    writeToBuildFile(TEST_SUITE_MODEL_PARSE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = android(buildModel);
    assertNotNull(android);

    TestSuiteModel testSuiteModel = android.testOptions().suites().get(0);

    // Re-apply the existing configuration
    testSuiteModel.addTargetVariant("debug");
    testSuiteModel.addTarget("default");
    testSuiteModel.addAssets();
    testSuiteModel.useJunitEngine().addInput("com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS");
    testSuiteModel.useJunitEngine().addIncludeEngine("test-engine-id");
    testSuiteModel.useJunitEngine().addEngineDependency("org.junit.platform:junit-platform-launcher");
    testSuiteModel.useJunitEngine().addEngineDependency("org.junit.platform:junit-platform-engine:1.12.0");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TEST_SUITE_MODEL_PARSE_ELEMENTS);

    assertNotNull(testSuiteModel);
    assertEquals("name", "testSuite", testSuiteModel.name());
    assertEquals("targetVariants", List.of("debug", "release"), testSuiteModel.targetVariants());
    assertNotNull(testSuiteModel.assets());
    assertEquals("targets", List.of("default"), testSuiteModel.targets().stream().map(TargetModel::name).toList());

    assertEquals("useJunitEngine.inputs", List.of("com.android.build.api.dsl.AgpTestSuiteInputParameters.TESTED_APKS"),
                 testSuiteModel.useJunitEngine().inputs());
    assertEquals("useJunitEngine.includeEngines", List.of("test-engine-id"), testSuiteModel.useJunitEngine().includeEngines());
    assertEquals("useJunitEngine.enginesDependencies",
                 List.of("org.junit.platform:junit-platform-launcher",
                         "org.junit.platform:junit-platform-engine:1.12.0"), testSuiteModel.useJunitEngine().enginesDependencies());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TEST_SUITE_MODEL_PARSE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = android(buildModel);
    assertNotNull(android);

    TestSuiteModel testSuiteModel = android.testOptions().suites().get(0);
    testSuiteModel.assets().delete();
    testSuiteModel.targets().forEach(DeletablePsiElementHolder::delete);
    testSuiteModel.targetVariants().delete();
    testSuiteModel.useJunitEngine().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TEST_SUITE_MODEL_ADD_ELEMENTS);

    android = android(buildModel);
    assertNotNull(android);

    testSuiteModel = android.testOptions().suites().get(0);
    assertNotNull(testSuiteModel);
    assertEmpty(testSuiteModel.targets());
    assertNull(testSuiteModel.targetVariants().getListValue(String.class));
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TEST_SUITE_MODEL_PARSE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = android(buildModel);
    assertNotNull(android);

    TestSuiteModel testSuiteModel = android.testOptions().suites().get(0);
    testSuiteModel.addTargetVariant("freeDebug");
    testSuiteModel.addTarget("newTarget");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TEST_SUITE_MODEL_EDIT_ELEMENTS_EXPECTED);

    android = android(buildModel);
    assertNotNull(android);

    testSuiteModel = android.testOptions().suites().get(0);
    assertEquals("testSuites.0.targetVariants", List.of("debug", "release", "freeDebug"), testSuiteModel.targetVariants());
    assertEquals("testSuites.0.targets", List.of("default", "newTarget"),
                 testSuiteModel.targets().stream().map(TargetModel::name).toList());
  }
}
