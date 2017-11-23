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
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.AdbOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link AdbOptionsModel}.
 */
public class AdbOptionsModelTest extends GradleFileModelTestCase {
  public void testParseElementsOne() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "    installOptions 'abcd'\n" +
                  "    timeOutInMs 100\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(100), adbOptions.timeOutInMs());
  }

  public void testParseElementsTwo() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "    installOptions 'abcd', 'efgh'\n" +
                  "    timeOutInMs = 200\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(200), adbOptions.timeOutInMs());
  }

  public void testEditElements() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "    installOptions 'abcd', 'efgh'\n" +
                  "    timeOutInMs = 200\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(200), adbOptions.timeOutInMs());

    adbOptions
      .replaceInstallOption("efgh", "xyz")
      .setTimeOutInMs(300);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "xyz"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(300), adbOptions.timeOutInMs());
  }

  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertNull("installOptions", adbOptions.installOptions());
    assertNull("timeOutInMs", adbOptions.timeOutInMs());

    adbOptions.addInstallOption("abcd");
    adbOptions.setTimeOutInMs(100);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(100), adbOptions.timeOutInMs());
  }

  public void testRemoveElements() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "    installOptions 'abcd', 'efgh'\n" +
                  "    timeOutInMs = 200\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    checkForValidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());
    assertEquals("timeOutInMs", Integer.valueOf(200), adbOptions.timeOutInMs());

    adbOptions
      .removeAllInstallOptions()
      .removeTimeOutInMs();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    checkForInValidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertNull("installOptions", adbOptions.installOptions());
    assertNull("timeOutInMs", adbOptions.timeOutInMs());
  }

  public void testRemoveOneOfElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "    installOptions 'abcd', 'efgh'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("abcd", "efgh"), adbOptions.installOptions());

    adbOptions.removeInstallOption("abcd");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    assertEquals("installOptions", ImmutableList.of("efgh"), adbOptions.installOptions());
  }

  public void testRemoveOnlyElementInTheList() throws Exception {
    String text = "android {\n" +
                  "  adbOptions {\n" +
                  "    installOptions 'abcd'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AdbOptionsModel adbOptions = android.adbOptions();
    checkForValidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertEquals("installOptions", ImmutableList.of("abcd"), adbOptions.installOptions());

    adbOptions.removeInstallOption("abcd");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    adbOptions = android.adbOptions();
    checkForInValidPsiElement(adbOptions, AdbOptionsModelImpl.class);
    assertNull("installOptions", adbOptions.installOptions());
  }
}
