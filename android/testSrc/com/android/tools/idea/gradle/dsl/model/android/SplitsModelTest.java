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
import com.android.tools.idea.gradle.dsl.api.android.SplitsModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.AbiModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.DensityModel;
import com.android.tools.idea.gradle.dsl.api.android.splits.LanguageModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link SplitsModel}.
 */
public class SplitsModelTest extends GradleFileModelTestCase {
  private static final String SPLITS_TEXT = "android {\n" +
                                            "  splits {\n" +
                                            "    abi {\n" +
                                            "      enable true\n" +
                                            "      exclude 'abi-exclude-1', 'abi-exclude-2'\n" +
                                            "      include 'abi-include-1', 'abi-include-2'\n" +
                                            "      universalApk false\n" +
                                            "    }\n" +
                                            "    density {\n" +
                                            "      auto false\n" +
                                            "      compatibleScreens 'screen1', 'screen2'\n" +
                                            "      enable true\n" +
                                            "      exclude 'density-exclude-1', 'density-exclude-2'\n" +
                                            "      include 'density-include-1', 'density-include-2'\n" +
                                            "    }\n" +
                                            "    language {\n" +
                                            "      enable false\n" +
                                            "      include 'language-include-1', 'language-include-2'\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";

  public void testParseElements() throws Exception {
    writeToBuildFile(SPLITS_TEXT);
    verifySplitsValues();
  }

  public void testEditElements() throws Exception {
    writeToBuildFile(SPLITS_TEXT);
    verifySplitsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    abi.setEnable(false);
    abi.replaceExclude("abi-exclude-2", "abi-exclude-3");
    abi.replaceInclude("abi-include-2", "abi-include-3");
    abi.setUniversalApk(true);

    DensityModel density = splits.density();
    density.setAuto(true);
    density.replaceCompatibleScreen("screen2", "screen3");
    density.setEnable(false);
    density.replaceExclude("density-exclude-2", "density-exclude-3");
    density.replaceInclude("density-include-2", "density-include-3");

    LanguageModel language = splits.language();
    language.setEnable(true);
    language.replaceInclude("language-include-2", "language-include-3");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertEquals("enable", Boolean.FALSE, abi.enable());
    assertEquals("exclude", ImmutableList.of("abi-exclude-1", "abi-exclude-3"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1", "abi-include-3"), abi.include());
    assertEquals("universalApk", Boolean.TRUE, abi.universalApk());

    density = splits.density();
    assertEquals("auto", Boolean.TRUE, density.auto());
    assertEquals("compatibleScreens", ImmutableList.of("screen1", "screen3"), density.compatibleScreens());
    assertEquals("enable", Boolean.FALSE, density.enable());
    assertEquals("exclude", ImmutableList.of("density-exclude-1", "density-exclude-3"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-1", "density-include-3"), density.include());

    language = splits.language();
    assertEquals("enable", Boolean.TRUE, language.enable());
    assertEquals("include", ImmutableList.of("language-include-1", "language-include-3"), language.include());
  }

  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    verifyNullSplitsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    abi.setEnable(true);
    abi.addExclude("abi-exclude");
    abi.addInclude("abi-include");
    abi.setUniversalApk(false);

    DensityModel density = splits.density();
    density.setAuto(false);
    density.addCompatibleScreen("screen");
    density.setEnable(true);
    density.addExclude("density-exclude");
    density.addInclude("density-include");

    LanguageModel language = splits.language();
    language.setEnable(false);
    language.addInclude("language-include");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertEquals("enable", Boolean.TRUE, abi.enable());
    assertEquals("exclude", ImmutableList.of("abi-exclude"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include"), abi.include());
    assertEquals("universalApk", Boolean.FALSE, abi.universalApk());

    density = splits.density();
    assertEquals("auto", Boolean.FALSE, density.auto());
    assertEquals("compatibleScreens", ImmutableList.of("screen"), density.compatibleScreens());
    assertEquals("enable", Boolean.TRUE, density.enable());
    assertEquals("exclude", ImmutableList.of("density-exclude"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include"), density.include());

    language = splits.language();
    assertEquals("enable", Boolean.FALSE, language.enable());
    assertEquals("include", ImmutableList.of("language-include"), language.include());
  }

  public void testRemoveElements() throws Exception {
    writeToBuildFile(SPLITS_TEXT);
    verifySplitsValues();

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(hasPsiElement(splits));

    AbiModel abi = splits.abi();
    assertTrue(hasPsiElement(abi));
    abi.removeEnable();
    abi.removeAllExclude();
    abi.removeAllInclude();
    abi.removeUniversalApk();

    DensityModel density = splits.density();
    assertTrue(hasPsiElement(density));
    density.removeAuto();
    density.removeAllCompatibleScreens();
    density.removeEnable();
    density.removeAllExclude();
    density.removeAllInclude();

    LanguageModel language = splits.language();
    assertTrue(hasPsiElement(language));
    language.removeEnable();
    language.removeAllInclude();

    applyChangesAndReparse(buildModel);
    verifyNullSplitsValues();
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertFalse(hasPsiElement(splits));
  }

  private void verifySplitsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    assertEquals("enable", Boolean.TRUE, abi.enable());
    assertEquals("exclude", ImmutableList.of("abi-exclude-1", "abi-exclude-2"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1", "abi-include-2"), abi.include());
    assertEquals("universalApk", Boolean.FALSE, abi.universalApk());

    DensityModel density = splits.density();
    assertEquals("auto", Boolean.FALSE, density.auto());
    assertEquals("compatibleScreens", ImmutableList.of("screen1", "screen2"), density.compatibleScreens());
    assertEquals("enable", Boolean.TRUE, density.enable());
    assertEquals("exclude", ImmutableList.of("density-exclude-1", "density-exclude-2"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-1", "density-include-2"), density.include());

    LanguageModel language = splits.language();
    assertEquals("enable", Boolean.FALSE, language.enable());
    assertEquals("include", ImmutableList.of("language-include-1", "language-include-2"), language.include());
  }

  public void verifyNullSplitsValues() {
    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    assertNull("enable", abi.enable());
    assertNull("exclude", abi.exclude());
    assertNull("include", abi.include());
    assertNull("universalApk", abi.universalApk());
    assertFalse(hasPsiElement(abi));

    DensityModel density = splits.density();
    assertNull("auto", density.auto());
    assertNull("compatibleScreens", density.compatibleScreens());
    assertNull("enable", density.enable());
    assertNull("exclude", density.exclude());
    assertNull("include", density.include());
    assertFalse(hasPsiElement(density));

    LanguageModel language = splits.language();
    assertNull("enable", language.enable());
    assertNull("include", language.include());
    assertFalse(hasPsiElement(language));
  }

  public void testRemoveBlockElements() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "    }\n" +
                  "    density {\n" +
                  "    }\n" +
                  "    language {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(hasPsiElement(splits));
    assertTrue(hasPsiElement(splits.abi()));
    assertTrue(hasPsiElement(splits.density()));
    assertTrue(hasPsiElement(splits.language()));

    splits.removeAbi();
    splits.removeDensity();
    splits.removeLanguage();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertFalse(hasPsiElement(splits));
    assertFalse(hasPsiElement(splits.abi()));
    assertFalse(hasPsiElement(splits.density()));
    assertFalse(hasPsiElement(splits.language()));
  }

  public void testRemoveOneOfElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "      exclude 'abi-exclude-1', 'abi-exclude-2'\n" +
                  "      include 'abi-include-1', 'abi-include-2'\n" +
                  "    }\n" +
                  "    density {\n" +
                  "      compatibleScreens 'screen1', 'screen2'\n" +
                  "      exclude 'density-exclude-1', 'density-exclude-2'\n" +
                  "      include 'density-include-1', 'density-include-2'\n" +
                  "    }\n" +
                  "    language {\n" +
                  "      include 'language-include-1', 'language-include-2'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();

    AbiModel abi = splits.abi();
    assertEquals("exclude", ImmutableList.of("abi-exclude-1", "abi-exclude-2"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1", "abi-include-2"), abi.include());

    DensityModel density = splits.density();
    assertEquals("compatibleScreens", ImmutableList.of("screen1", "screen2"), density.compatibleScreens());
    assertEquals("exclude", ImmutableList.of("density-exclude-1", "density-exclude-2"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-1", "density-include-2"), density.include());

    LanguageModel language = splits.language();
    assertEquals("include", ImmutableList.of("language-include-1", "language-include-2"), language.include());

    abi.removeExclude("abi-exclude-1");
    abi.removeInclude("abi-include-2");
    density.removeCompatibleScreen("screen1");
    density.removeExclude("density-exclude-2");
    density.removeInclude("density-include-1");
    language.removeInclude("language-include-2");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertEquals("exclude", ImmutableList.of("abi-exclude-2"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include-1"), abi.include());

    density = splits.density();
    assertEquals("compatibleScreens", ImmutableList.of("screen2"), density.compatibleScreens());
    assertEquals("exclude", ImmutableList.of("density-exclude-1"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include-2"), density.include());

    language = splits.language();
    assertEquals("include", ImmutableList.of("language-include-1"), language.include());
  }

  public void testRemoveOnlyElementsInTheList() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "      exclude 'abi-exclude'\n" +
                  "      include 'abi-include'\n" +
                  "    }\n" +
                  "    density {\n" +
                  "      compatibleScreens 'screen'\n" +
                  "      exclude 'density-exclude'\n" +
                  "      include 'density-include'\n" +
                  "    }\n" +
                  "    language {\n" +
                  "      include 'language-include'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertTrue(hasPsiElement(splits));

    AbiModel abi = splits.abi();
    assertTrue(hasPsiElement(abi));
    assertEquals("exclude", ImmutableList.of("abi-exclude"), abi.exclude());
    assertEquals("include", ImmutableList.of("abi-include"), abi.include());

    DensityModel density = splits.density();
    assertTrue(hasPsiElement(density));
    assertEquals("compatibleScreens", ImmutableList.of("screen"), density.compatibleScreens());
    assertEquals("exclude", ImmutableList.of("density-exclude"), density.exclude());
    assertEquals("include", ImmutableList.of("density-include"), density.include());

    LanguageModel language = splits.language();
    assertTrue(hasPsiElement(language));
    assertEquals("include", ImmutableList.of("language-include"), language.include());

    abi.removeExclude("abi-exclude");
    abi.removeInclude("abi-include");
    density.removeCompatibleScreen("screen");
    density.removeExclude("density-exclude");
    density.removeInclude("density-include");
    language.removeInclude("language-include");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();

    abi = splits.abi();
    assertNull("exclude", abi.exclude());
    assertNull("include", abi.include());
    assertFalse(hasPsiElement(abi));

    density = splits.density();
    assertNull("compatibleScreens", density.compatibleScreens());
    assertNull("exclude", density.exclude());
    assertNull("include", density.include());

    language = splits.language();
    assertNull("include", language.include());
    assertFalse(hasPsiElement(language));

    assertFalse(hasPsiElement(splits));
  }

  public void testResetStatement() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "      include 'abi-include-1', 'abi-include-2'\n" +
                  "      reset()\n" +
                  "    }\n" +
                  "    density {\n" +
                  "      include 'density-include-1', 'density-include-2'\n" +
                  "      reset()\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertNull("abi-include", splits.abi().include());
    assertNull("density-include", splits.density().include());
  }

  public void testResetAndInitialize() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "      include 'abi-include-1'\n" +
                  "      reset()\n" +
                  "      include 'abi-include-2', 'abi-include-3'\n" +
                  "    }\n" +
                  "    density {\n" +
                  "      include 'density-include-1', 'density-include-2'\n" +
                  "      reset()\n" +
                  "      include 'density-include-3'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertEquals("abi-include", ImmutableList.of("abi-include-2", "abi-include-3"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-3"), splits.density().include());
  }

  public void testAddResetStatement() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "      include 'abi-include-1', 'abi-include-2'\n" +
                  "    }\n" +
                  "    density {\n" +
                  "      include 'density-include-1', 'density-include-2'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertEquals("abi-include", ImmutableList.of("abi-include-1", "abi-include-2"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-1", "density-include-2"), splits.density().include());

    splits.abi().addReset();
    splits.density().addReset();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertNull("abi-include", splits.abi().include());
    assertNull("density-include", splits.density().include());
  }

  public void testRemoveResetStatement() throws Exception {
    String text = "android {\n" +
                  "  splits {\n" +
                  "    abi {\n" +
                  "      include 'abi-include-1', 'abi-include-2'\n" +
                  "      reset()\n" +
                  "    }\n" +
                  "    density {\n" +
                  "      include 'density-include-1', 'density-include-2'\n" +
                  "      reset()\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SplitsModel splits = android.splits();
    assertNull("abi-include", splits.abi().include());
    assertNull("density-include", splits.density().include());

    splits.abi().removeReset();
    splits.density().removeReset();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    splits = android.splits();
    assertEquals("abi-include", ImmutableList.of("abi-include-1", "abi-include-2"), splits.abi().include());
    assertEquals("density-include", ImmutableList.of("density-include-1", "density-include-2"), splits.density().include());
  }
}
