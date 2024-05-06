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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_OPTIONS_MODEL_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_OPTIONS_MODEL_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.TEST_OPTIONS_MODEL_TEST_OPTIONS_TEXT;

import com.android.tools.idea.gradle.model.IdeTestOptions;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.TestOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.junit.Test;

/**
 * Tests for {@link TestOptionsModel}.
 */
public class TestOptionsModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_MODEL_TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_MODEL_TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    testOptions.reportDir().setValue("otherReportDir");
    testOptions.resultsDir().setValue("otherResultsDir");
    testOptions.unitTests().returnDefaultValues().setValue(false);
    testOptions.failureRetention().enable().setValue(false);
    testOptions.failureRetention().maxSnapshots().setValue(4);
    testOptions.emulatorSnapshots().compressSnapshots().setValue(true);
    testOptions.emulatorSnapshots().enableForTestFailures().setValue(false);
    testOptions.emulatorSnapshots().maxSnapshotsForTestFailures().setValue(3);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TEST_OPTIONS_MODEL_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    testOptions = android.testOptions();
    assertEquals("reportDir", "otherReportDir", testOptions.reportDir());
    assertEquals("resultsDir", "otherResultsDir", testOptions.resultsDir());
    assertEquals("unitTests.returnDefaultValues", Boolean.FALSE, testOptions.unitTests().returnDefaultValues());
    assertEquals("failureRetention.enable", Boolean.FALSE, testOptions.failureRetention().enable());
    assertEquals("failureRetention.maxSnapshots", 4, testOptions.failureRetention().maxSnapshots());
    assertEquals("emulatorSnapshots.compressSnapshots", Boolean.TRUE, testOptions.emulatorSnapshots().compressSnapshots());
    assertEquals("emulatorSnapshots.enableForTestFailures", Boolean.FALSE, testOptions.emulatorSnapshots().enableForTestFailures());
    assertEquals("emulatorSnapshots.maxSnapshotsForTestFailures", 3, testOptions.emulatorSnapshots().maxSnapshotsForTestFailures());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_MODEL_ADD_ELEMENTS);
    verifyNullTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    testOptions.reportDir().setValue("reportDirectory");
    testOptions.resultsDir().setValue("resultsDirectory");
    testOptions.execution().setValue(IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR.name());
    testOptions.unitTests().returnDefaultValues().setValue(true);
    testOptions.failureRetention().enable().setValue(true);
    testOptions.failureRetention().maxSnapshots().setValue(3);
    testOptions.emulatorSnapshots().compressSnapshots().setValue(false);
    testOptions.emulatorSnapshots().enableForTestFailures().setValue(true);
    testOptions.emulatorSnapshots().maxSnapshotsForTestFailures().setValue(4);

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TEST_OPTIONS_MODEL_ADD_ELEMENTS_EXPECTED);

    verifyTestOptionsValues();
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_MODEL_TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    assertTrue(hasPsiElement(testOptions));
    testOptions.reportDir().delete();
    testOptions.resultsDir().delete();
    testOptions.execution().delete();
    testOptions.unitTests().returnDefaultValues().delete();
    testOptions.failureRetention().enable().delete();
    testOptions.failureRetention().maxSnapshots().delete();
    testOptions.emulatorSnapshots().compressSnapshots().delete();
    testOptions.emulatorSnapshots().enableForTestFailures().delete();
    testOptions.emulatorSnapshots().maxSnapshotsForTestFailures().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    testOptions = android.testOptions();
    verifyNullTestOptionsValues();
    assertFalse(hasPsiElement(testOptions));
  }

  private void verifyTestOptionsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    assertEquals("reportDir", "reportDirectory", testOptions.reportDir());
    assertEquals("resultsDir", "resultsDirectory", testOptions.resultsDir());
    assertEquals("execution", IdeTestOptions.Execution.ANDROID_TEST_ORCHESTRATOR.name(), testOptions.execution());
    assertEquals("unitTests.returnDefaultValues", Boolean.TRUE, testOptions.unitTests().returnDefaultValues());
    assertEquals("failureRetention.enable", Boolean.TRUE, testOptions.failureRetention().enable());
    assertEquals("failureRetention.maxSnapshots", 3, testOptions.failureRetention().maxSnapshots());
    assertEquals("emulatorSnapshots.compressSnapshots", Boolean.FALSE, testOptions.emulatorSnapshots().compressSnapshots());
    assertEquals("emulatorSnapshots.enableForTestFailures", Boolean.TRUE, testOptions.emulatorSnapshots().enableForTestFailures());
    assertEquals("emulatorSnapshots.maxSnapshotsForTestFailures", 4, testOptions.emulatorSnapshots().maxSnapshotsForTestFailures());
  }

  private void verifyNullTestOptionsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    assertMissingProperty("reportDir", testOptions.reportDir());
    assertMissingProperty("resultsDir", testOptions.resultsDir());
    assertMissingProperty("execution", testOptions.execution());
    assertMissingProperty("unitTests.returnDefaultValues", testOptions.unitTests().returnDefaultValues());
    assertMissingProperty("failureRetention.enable", testOptions.failureRetention().enable());
    assertMissingProperty("failureRetention.maxSnapshots", testOptions.failureRetention().maxSnapshots());
    assertMissingProperty("emulatorSnapshots.compressSnapshots", testOptions.emulatorSnapshots().compressSnapshots());
    assertMissingProperty("emulatorSnapshots.enableForTestFailures", testOptions.emulatorSnapshots().enableForTestFailures());
    assertMissingProperty("emulatorSnapshots.maxSnapshotsForTestFailures", testOptions.emulatorSnapshots().maxSnapshotsForTestFailures());
  }
}
