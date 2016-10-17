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
package com.android.tools.idea.gradle.dsl.model.values;

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;

/**
 * Tests for {@link GradleValue}.
 */
public class GradleValueTest extends GradleFileModelTestCase {
  public void testGradleValuesOfLiteralElementsInApplicationStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion \"23.0.0\"\n" +
                  "  compileSdkVersion 23\n" +
                  "  defaultPublishConfig \"debug\"\n" +
                  "  generatePureSplits true\n" +
                  "  publishNonDefault false\n" +
                  "  resourcePrefix \"abcd\"\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    verifyGradleValue(android.buildToolsVersion(), "android.buildToolsVersion", "\"23.0.0\"");
    verifyGradleValue(android.compileSdkVersion(), "android.compileSdkVersion", "23");
    verifyGradleValue(android.defaultPublishConfig(), "android.defaultPublishConfig", "\"debug\"");
    verifyGradleValue(android.generatePureSplits(), "android.generatePureSplits", "true");
    verifyGradleValue(android.publishNonDefault(), "android.publishNonDefault", "false");
    verifyGradleValue(android.resourcePrefix(), "android.resourcePrefix", "\"abcd\"");
  }

  public void testGradleValuesOfLiteralElementsInAssignmentStatements() throws Exception {
    String text = "android { \n" +
                  "  buildToolsVersion = \"23.0.0\"\n" +
                  "  compileSdkVersion = \"android-23\"\n" +
                  "  defaultPublishConfig = \"debug\"\n" +
                  "  generatePureSplits = true\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    verifyGradleValue(android.buildToolsVersion(), "android.buildToolsVersion", "\"23.0.0\"");
    verifyGradleValue(android.compileSdkVersion(), "android.compileSdkVersion", "\"android-23\"");
    verifyGradleValue(android.defaultPublishConfig(), "android.defaultPublishConfig", "\"debug\"");
    verifyGradleValue(android.generatePureSplits(), "android.generatePureSplits", "true");
  }
}
