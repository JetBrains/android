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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.TestOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;

/**
 * Tests for {@link TestOptionsModel}.
 */
public class TestOptionsModelTest extends GradleFileModelTestCase {
  private static final String TEST_OPTIONS_TEXT = "android {\n" +
                                                  "  testOptions {\n" +
                                                  "    reportDir 'reportDirectory'\n" +
                                                  "    resultsDir 'resultsDirectory'\n" +
                                                  "    unitTests.returnDefaultValues true\n" +
                                                  "  }\n" +
                                                  "}";

  public void testParseElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();
  }

  public void testEditElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    testOptions.setReportDir("otherReportDir");
    testOptions.setResultsDir("otherResultsDir");
    testOptions.unitTests().setReturnDefaultValues(false);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    testOptions = android.testOptions();
    assertEquals("reportDir", "otherReportDir", testOptions.reportDir());
    assertEquals("resultsDir", "otherResultsDir", testOptions.resultsDir());
    assertEquals("unitTests.returnDefaultValues", Boolean.FALSE, testOptions.unitTests().returnDefaultValues());
  }

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
    testOptions.setReportDir("reportDirectory");
    testOptions.setResultsDir("resultsDirectory");
    testOptions.unitTests().setReturnDefaultValues(true);

    applyChangesAndReparse(buildModel);
    verifyTestOptionsValues();
  }

  public void testRemoveElements() throws Exception {
    writeToBuildFile(TEST_OPTIONS_TEXT);
    verifyTestOptionsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    assertTrue(hasPsiElement(testOptions));
    testOptions.removeReportDir();
    testOptions.removeResultsDir();
    testOptions.unitTests().removeReturnDefaultValues();

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
    assertEquals("unitTests.returnDefaultValues", Boolean.TRUE, testOptions.unitTests().returnDefaultValues());
  }

  private void verifyNullTestOptionsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    TestOptionsModel testOptions = android.testOptions();
    assertNull("reportDir", testOptions.reportDir());
    assertNull("resultsDir", testOptions.resultsDir());
    assertNull("unitTests.returnDefaultValues", testOptions.unitTests().returnDefaultValues());
  }
}
