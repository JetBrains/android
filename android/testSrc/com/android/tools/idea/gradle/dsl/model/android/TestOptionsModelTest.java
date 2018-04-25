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

import com.android.builder.model.TestOptions;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.TestOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.junit.Test;

/**
 * Tests for {@link TestOptionsModel}.
 */
public class TestOptionsModelTest extends GradleFileModelTestCase {
  private static final String TEST_OPTIONS_TEXT = "android {\n" +
                                                  "  testOptions {\n" +
                                                  "    reportDir 'reportDirectory'\n" +
                                                  "    resultsDir 'resultsDirectory'\n" +
                                                  "    unitTests.returnDefaultValues true\n" +
                                                  "    execution 'ANDROID_TEST_ORCHESTRATOR'" +
                                                  "  }\n" +
                                                  "}";

  @Test
  public void testParseElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    testOptions.reportDir().setValue("otherReportDir");
    testOptions.resultsDir().setValue("otherResultsDir");
    testOptions.unitTests().returnDefaultValues().setValue(false);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    testOptions = android.testOptions();
    assertEquals("reportDir", "otherReportDir", testOptions.reportDir());
    assertEquals("resultsDir", "otherResultsDir", testOptions.resultsDir());
    assertEquals("unitTests.returnDefaultValues", Boolean.FALSE, testOptions.unitTests().returnDefaultValues());
  }

  @Test
  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  testOptions {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    verifyNullTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    testOptions.reportDir().setValue("reportDirectory");
    testOptions.resultsDir().setValue("resultsDirectory");
    testOptions.execution().setValue(TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR.name());
    testOptions.unitTests().returnDefaultValues().setValue(true);

    applyChangesAndReparse(buildModel);
    verifyTestOptionsValues();
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_TEXT);
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

    applyChangesAndReparse(buildModel);
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
    assertEquals("execution", TestOptions.Execution.ANDROID_TEST_ORCHESTRATOR.name(), testOptions.execution());
    assertEquals("unitTests.returnDefaultValues", Boolean.TRUE, testOptions.unitTests().returnDefaultValues());
  }

  private void verifyNullTestOptionsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    assertMissingProperty("reportDir", testOptions.reportDir());
    assertMissingProperty("resultsDir", testOptions.resultsDir());
    assertMissingProperty("execution", testOptions.execution());
    assertMissingProperty("unitTests.returnDefaultValues", testOptions.unitTests().returnDefaultValues());
  }
}
