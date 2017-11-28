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
import com.android.tools.idea.gradle.dsl.api.android.AaptOptionsModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link AaptOptionsModel}.
 */
public class AaptOptionsModelTest extends GradleFileModelTestCase {
  public void testParseElementsOne() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "    additionalParameters 'abcd'\n" +
                  "    cruncherEnabled true\n" +
                  "    cruncherProcesses 1\n" +
                  "    failOnMissingConfigEntry false\n" +
                  "    ignoreAssets 'efgh'\n" +
                  "    noCompress 'a'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());
  }

  public void testParseElementsTwo() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    cruncherEnabled false\n" +
                  "    cruncherProcesses 2\n" +
                  "    failOnMissingConfigEntry true\n" +
                  "    ignoreAssetsPattern 'ijkl'\n" +
                  "    noCompress 'a', 'b'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.FALSE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(2), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.TRUE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "ijkl", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "b"), aaptOptions.noCompress());
  }

  public void testEditElements() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    cruncherEnabled false\n" +
                  "    cruncherProcesses 2\n" +
                  "    failOnMissingConfigEntry true\n" +
                  "    ignoreAssets 'ijkl'\n" +
                  "    noCompress 'a', 'b'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.FALSE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(2), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.TRUE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "ijkl", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "b"), aaptOptions.noCompress());

    aaptOptions
      .replaceAdditionalParameter("efgh", "xyz")
      .setCruncherEnabled(true)
      .setCruncherProcesses(3)
      .setFailOnMissingConfigEntry(false)
      .setIgnoreAssets("mnop")
      .replaceNoCompress("b", "c");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "xyz"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(3), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "mnop", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "c"), aaptOptions.noCompress());
  }

  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertNull("additionalParameters", aaptOptions.additionalParameters());
    assertNull("cruncherEnabled", aaptOptions.cruncherEnabled());
    assertNull("cruncherProcesses", aaptOptions.cruncherProcesses());
    assertNull("failOnMissingConfigEntry", aaptOptions.failOnMissingConfigEntry());
    assertNull("ignoreAssets", aaptOptions.ignoreAssets());
    assertNull("noCompress", aaptOptions.noCompress());

    aaptOptions.addAdditionalParameter("abcd");
    aaptOptions.setCruncherEnabled(true);
    aaptOptions.setCruncherProcesses(1);
    aaptOptions.setFailOnMissingConfigEntry(false);
    aaptOptions.setIgnoreAssets("efgh");
    aaptOptions.addNoCompress("a");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());
  }

  public void testRemoveElements() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    cruncherEnabled true\n" +
                  "    cruncherProcesses 1\n" +
                  "    failOnMissingConfigEntry false\n" +
                  "    ignoreAssets 'efgh'\n" +
                  "    noCompress 'a'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    checkForValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, aaptOptions.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), aaptOptions.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, aaptOptions.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", aaptOptions.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());

    aaptOptions
      .removeAllAdditionalParameters()
      .removeCruncherEnabled()
      .removeCruncherProcesses()
      .removeFailOnMissingConfigEntry()
      .removeIgnoreAssets()
      .removeAllNoCompress();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    checkForInValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertNull("additionalParameters", aaptOptions.additionalParameters());
    assertNull("cruncherEnabled", aaptOptions.cruncherEnabled());
    assertNull("cruncherProcesses", aaptOptions.cruncherProcesses());
    assertNull("failOnMissingConfigEntry", aaptOptions.failOnMissingConfigEntry());
    assertNull("ignoreAssets", aaptOptions.ignoreAssets());
    assertNull("noCompress", aaptOptions.noCompress());
  }

  public void testRemoveOneOfElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "    additionalParameters 'abcd', 'efgh'\n" +
                  "    noCompress 'a', 'b'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), aaptOptions.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a", "b"), aaptOptions.noCompress());

    aaptOptions.removeAdditionalParameter("abcd");
    aaptOptions.removeNoCompress("b");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    assertEquals("additionalParameters", ImmutableList.of("efgh"), aaptOptions.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());
  }

  public void testRemoveOnlyElementInTheList() throws Exception {
    String text = "android {\n" +
                  "  aaptOptions {\n" +
                  "    additionalParameters 'abcd'\n" +
                  "    noCompress 'a'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AaptOptionsModel aaptOptions = android.aaptOptions();
    checkForValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd"), aaptOptions.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a"), aaptOptions.noCompress());

    aaptOptions.removeAdditionalParameter("abcd");
    aaptOptions.removeNoCompress("a");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    aaptOptions = android.aaptOptions();
    checkForInValidPsiElement(aaptOptions, AaptOptionsModelImpl.class);
    assertNull("additionalParameters", aaptOptions.additionalParameters());
    assertNull("noCompress", aaptOptions.noCompress());
  }
}
