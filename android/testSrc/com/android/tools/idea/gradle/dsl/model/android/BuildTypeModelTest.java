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
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.FlavorTypeModelImpl.ResValueImpl;
import com.android.tools.idea.gradle.dsl.model.android.BuildTypeModelImpl.BuildConfigFieldImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link BuildTypeModelImpl}.
 */
public class BuildTypeModelTest extends GradleFileModelTestCase {
  public void testBuildTypeBlockWithApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp true\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "      minifyEnabled true\n" +
                  "      multiDexEnabled true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled true\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      shrinkResources true\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack true\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  public void testBuildTypeBlockWithAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix = \"mySuffix\"\n" +
                  "      consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "      debuggable = true\n" +
                  "      embedMicroApp = true\n" +
                  "      jniDebuggable = true\n" +
                  "      manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "      minifyEnabled = true\n" +
                  "      multiDexEnabled = true\n" +
                  "      proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "      pseudoLocalesEnabled = true\n" +
                  "      renderscriptDebuggable = true\n" +
                  "      renderscriptOptimLevel = 1\n" +
                  "      shrinkResources = true\n" +
                  "      testCoverageEnabled = true\n" +
                  "      useJack = true\n" +
                  "      versionNameSuffix = \"abc\"\n" +
                  "      zipAlignEnabled = true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  public void testBuildTypeApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz.applicationIdSuffix \"mySuffix\"\n" +
                  "android.buildTypes.xyz.buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "android.buildTypes.xyz.consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "android.buildTypes.xyz.debuggable true\n" +
                  "android.buildTypes.xyz.embedMicroApp true\n" +
                  "android.buildTypes.xyz.jniDebuggable true\n" +
                  "android.buildTypes.xyz.manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "android.buildTypes.xyz.minifyEnabled true\n" +
                  "android.buildTypes.xyz.multiDexEnabled true\n" +
                  "android.buildTypes.xyz.proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "android.buildTypes.xyz.pseudoLocalesEnabled true\n" +
                  "android.buildTypes.xyz.renderscriptDebuggable true\n" +
                  "android.buildTypes.xyz.renderscriptOptimLevel 1\n" +
                  "android.buildTypes.xyz.resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "android.buildTypes.xyz.shrinkResources true\n" +
                  "android.buildTypes.xyz.testCoverageEnabled true\n" +
                  "android.buildTypes.xyz.useJack true\n" +
                  "android.buildTypes.xyz.versionNameSuffix \"abc\"\n" +
                  "android.buildTypes.xyz.zipAlignEnabled true";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  public void testBuildTypeAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz.applicationIdSuffix = \"mySuffix\"\n" +
                  "android.buildTypes.xyz.consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "android.buildTypes.xyz.debuggable = true\n" +
                  "android.buildTypes.xyz.embedMicroApp = true\n" +
                  "android.buildTypes.xyz.jniDebuggable = true\n" +
                  "android.buildTypes.xyz.manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "android.buildTypes.xyz.minifyEnabled = true\n" +
                  "android.buildTypes.xyz.multiDexEnabled = true\n" +
                  "android.buildTypes.xyz.proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "android.buildTypes.xyz.pseudoLocalesEnabled = true\n" +
                  "android.buildTypes.xyz.renderscriptDebuggable = true\n" +
                  "android.buildTypes.xyz.renderscriptOptimLevel = 1\n" +
                  "android.buildTypes.xyz.shrinkResources = true\n" +
                  "android.buildTypes.xyz.testCoverageEnabled = true\n" +
                  "android.buildTypes.xyz.useJack = true\n" +
                  "android.buildTypes.xyz.versionNameSuffix = \"abc\"\n" +
                  "android.buildTypes.xyz.zipAlignEnabled = true";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  public void testBuildTypeBlockWithOverrideStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      consumerProguardFiles = ['proguard-android.txt', 'proguard-rules.pro']\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp = false\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders = [activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"]\n" +
                  "      minifyEnabled false\n" +
                  "      multiDexEnabled = true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled = false\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel = 1\n" +
                  "      shrinkResources false\n" +
                  "      testCoverageEnabled = true\n" +
                  "      useJack false\n" +
                  "      versionNameSuffix = \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz {\n" +
                  "  applicationIdSuffix = \"mySuffix-1\"\n" +
                  "  consumerProguardFiles 'proguard-android-1.txt', 'proguard-rules-1.pro'\n" +
                  "  debuggable = false\n" +
                  "  embedMicroApp true\n" +
                  "  jniDebuggable = false\n" +
                  "  manifestPlaceholders activityLabel3:\"defaultName3\", activityLabel4:\"defaultName4\"\n" +
                  "  minifyEnabled = true\n" +
                  "  multiDexEnabled false\n" +
                  "  proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.pro']\n" +
                  "  pseudoLocalesEnabled true\n" +
                  "  renderscriptDebuggable = false\n" +
                  "  renderscriptOptimLevel 2\n" +
                  "  shrinkResources = true\n" +
                  "  testCoverageEnabled false\n" +
                  "  useJack true\n" +
                  "  versionNameSuffix = \"abc-1\"\n" +
                  "  zipAlignEnabled = false\n" +
                  "}\n" +
                  "android.buildTypes.xyz.applicationIdSuffix = \"mySuffix-3\"";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix-3", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel3", "defaultName3", "activityLabel4", "defaultName4"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc-1", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  public void testBuildTypeBlockWithAppendStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      proguardFiles 'pro-1.txt', 'pro-2.txt'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz {\n" +
                  "  buildConfigField \"cdef\", \"ghij\", \"klmn\"\n" +
                  "  manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "  proguardFile 'pro-3.txt'\n" +
                  "  resValue \"opqr\", \"stuv\", \"wxyz\"\n" +
                  "}\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel3 \"defaultName3\"\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel4 = \"defaultName4\"\n" +
                  "android.buildTypes.xyz.proguardFiles 'pro-4.txt', 'pro-5.txt'\n";

    writeToBuildFile(text);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("manifestPlaceholders",
                 ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2", "activityLabel3", "defaultName3",
                                 "activityLabel4", "defaultName4"),
                 buildType.manifestPlaceholders());
    assertEquals("proguardFiles",
                 ImmutableList.of("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
                 buildType.proguardFiles());
    assertEquals("resValues",
                 ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());
  }

  public void testBuildTypeMapStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel1 \"defaultName1\"\n" +
                  "android.buildTypes.xyz.manifestPlaceholders.activityLabel2 = \"defaultName2\"\n";

    writeToBuildFile(text);
    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  public void testRemoveAndResetElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp true\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "      minifyEnabled true\n" +
                  "      multiDexEnabled true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled true\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      shrinkResources true\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack true\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().delete();
    buildType.removeAllBuildConfigFields();
    buildType.removeAllConsumerProguardFiles();
    buildType.debuggable().delete();
    buildType.embedMicroApp().delete();
    buildType.jniDebuggable().delete();
    buildType.removeAllManifestPlaceholders();
    buildType.minifyEnabled().delete();
    buildType.multiDexEnabled().delete();
    buildType.removeAllProguardFiles();
    buildType.pseudoLocalesEnabled().delete();
    buildType.renderscriptDebuggable().delete();
    buildType.renderscriptOptimLevel().delete();
    buildType.removeAllResValues();
    buildType.shrinkResources().delete();
    buildType.testCoverageEnabled().delete();
    buildType.useJack().delete();
    buildType.versionNameSuffix().delete();
    buildType.zipAlignEnabled().delete();
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildModel.resetState();
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  public void testEditAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp false\n" +
                  "      jniDebuggable true\n" +
                  "      minifyEnabled false\n" +
                  "      multiDexEnabled true\n" +
                  "      pseudoLocalesEnabled false\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      shrinkResources false\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack false\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.FALSE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.FALSE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.FALSE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.FALSE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.FALSE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.resetState();
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.FALSE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.FALSE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.FALSE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.FALSE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.FALSE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  public void testAddAndResetLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.resetState();
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());
  }

  public void testReplaceAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.replaceBuildConfigField(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("abcd", "mnop", "qrst"));
    buildType.replaceConsumerProguardFile("proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceProguardFile("proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceResValue(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("mnop", "efgh", "ijkl"));
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "efgh", "ijkl")), buildType.resValues());

    buildModel.resetState();
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  public void testAddAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertNull("resValues", buildType.resValues());

    buildType.addBuildConfigField(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"));
    buildType.addConsumerProguardFile("proguard-android.txt");
    buildType.addProguardFile("proguard-android.txt");
    buildType.addResValue(new ResValueImpl("mnop", "qrst", "uvwx"));
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.resetState();
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertNull("resValues", buildType.resValues());
  }

  public void testAddToAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.addBuildConfigField(new BuildConfigFieldImpl("cdef", "ghij", "klmn"));
    buildType.addConsumerProguardFile("proguard-android-1.txt");
    buildType.addProguardFile("proguard-android-1.txt");
    buildType.addResValue(new ResValueImpl("opqr", "stuv", "wxyz"));
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildModel.resetState();
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  public void testRemoveFromAndResetListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      buildConfigField \"cdef\", \"ghij\", \"klmn\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      resValue \"opqr\", \"stuv\", \"wxyz\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildType.removeBuildConfigField(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"));
    buildType.removeConsumerProguardFile("proguard-rules.pro");
    buildType.removeProguardFile("proguard-rules.pro");
    buildType.removeResValue(new ResValueImpl("opqr", "stuv", "wxyz"));
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.resetState();
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());
  }

  public void testSetAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());

    buildType.setManifestPlaceholder("key1", 12345);
    buildType.setManifestPlaceholder("key3", true);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());
  }

  public void testAddAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.setManifestPlaceholder("activityLabel1", "newName1");
    buildType.setManifestPlaceholder("activityLabel2", "newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
  }

  public void testRemoveAndResetMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildType.removeManifestPlaceholder("activityLabel1");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  public void testRemoveAndApplyElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp true\n" +
                  "      jniDebuggable true\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "      minifyEnabled true\n" +
                  "      multiDexEnabled true\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      pseudoLocalesEnabled true\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      shrinkResources true\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack true\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());

    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertThat(buildType).isInstanceOf(BuildTypeModelImpl.class);
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    // Remove all the properties except the applicationIdSuffix.
    buildType.removeAllBuildConfigFields();
    buildType.removeAllConsumerProguardFiles();
    buildType.debuggable().delete();
    buildType.embedMicroApp().delete();
    buildType.jniDebuggable().delete();
    buildType.removeAllManifestPlaceholders();
    buildType.minifyEnabled().delete();
    buildType.multiDexEnabled().delete();
    buildType.removeAllProguardFiles();
    buildType.pseudoLocalesEnabled().delete();
    buildType.renderscriptDebuggable().delete();
    buildType.renderscriptOptimLevel().delete();
    buildType.removeAllResValues();
    buildType.shrinkResources().delete();
    buildType.testCoverageEnabled().delete();
    buildType.useJack().delete();
    buildType.versionNameSuffix().delete();
    buildType.zipAlignEnabled().delete();
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType).isInstanceOf(BuildTypeModelImpl.class);
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    applyChanges(buildModel);
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType).isInstanceOf(BuildTypeModelImpl.class);
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    buildType = getXyzBuildType(buildModel);
    assertThat(buildType).isInstanceOf(BuildTypeModelImpl.class);
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    // Now remove the applicationIdSuffix also and see the whole android block is removed as it would be an empty block.

    buildType.applicationIdSuffix().delete();
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType).isInstanceOf(BuildTypeModelImpl.class);
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());

    applyChanges(buildModel);
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType).isInstanceOf(BuildTypeModelImpl.class);
    assertFalse(((BuildTypeModelImpl)buildType).hasValidPsiElement());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(android.buildTypes()).isEmpty();
  }

  public void testEditAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      applicationIdSuffix \"mySuffix\"\n" +
                  "      debuggable true\n" +
                  "      embedMicroApp false\n" +
                  "      jniDebuggable true\n" +
                  "      minifyEnabled false\n" +
                  "      multiDexEnabled true\n" +
                  "      pseudoLocalesEnabled false\n" +
                  "      renderscriptDebuggable true\n" +
                  "      renderscriptOptimLevel 1\n" +
                  "      shrinkResources false\n" +
                  "      testCoverageEnabled true\n" +
                  "      useJack false\n" +
                  "      versionNameSuffix \"abc\"\n" +
                  "      zipAlignEnabled true\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.FALSE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.FALSE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.FALSE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.FALSE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.FALSE, buildType.useJack());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    applyChanges(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  public void testAddAndApplyLiteralElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertNull("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.debuggable().setValue(false);
    buildType.embedMicroApp().setValue(true);
    buildType.jniDebuggable().setValue(false);
    buildType.minifyEnabled().setValue(true);
    buildType.multiDexEnabled().setValue(false);
    buildType.pseudoLocalesEnabled().setValue(true);
    buildType.renderscriptDebuggable().setValue(false);
    buildType.renderscriptOptimLevel().setValue(2);
    buildType.shrinkResources().setValue(true);
    buildType.testCoverageEnabled().setValue(false);
    buildType.useJack().setValue(true);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    applyChanges(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  public void testReplaceAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.replaceBuildConfigField(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("abcd", "mnop", "qrst"));
    buildType.replaceConsumerProguardFile("proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceProguardFile("proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceResValue(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("mnop", "efgh", "ijkl"));
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "efgh", "ijkl")), buildType.resValues());

    applyChanges(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "efgh", "ijkl")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "efgh", "ijkl")), buildType.resValues());
  }

  public void testAddAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertNull("buildConfigFields", buildType.buildConfigFields());
    assertNull("consumerProguardFiles", buildType.consumerProguardFiles());
    assertNull("proguardFiles", buildType.proguardFiles());
    assertNull("resValues", buildType.resValues());

    buildType.addBuildConfigField(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"));
    buildType.addConsumerProguardFile("proguard-android.txt");
    buildType.addProguardFile("proguard-android.txt");
    buildType.addResValue(new ResValueImpl("mnop", "qrst", "uvwx"));
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    applyChanges(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  public void testAddToAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);

    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.addBuildConfigField(new BuildConfigFieldImpl("cdef", "ghij", "klmn"));
    buildType.addConsumerProguardFile("proguard-android-1.txt");
    buildType.addProguardFile("proguard-android-1.txt");
    buildType.addResValue(new ResValueImpl("opqr", "stuv", "wxyz"));
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    applyChanges(buildModel);
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());
  }

  public void testRemoveFromAndApplyListElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      buildConfigField \"abcd\", \"efgh\", \"ijkl\"\n" +
                  "      buildConfigField \"cdef\", \"ghij\", \"klmn\"\n" +
                  "      consumerProguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      proguardFiles 'proguard-android.txt', 'proguard-rules.pro'\n" +
                  "      resValue \"mnop\", \"qrst\", \"uvwx\"\n" +
                  "      resValue \"opqr\", \"stuv\", \"wxyz\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields",
                 ImmutableList.of(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"), new BuildConfigFieldImpl("cdef", "ghij", "klmn")),
                 buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx"), new ResValueImpl("opqr", "stuv", "wxyz")),
                 buildType.resValues());

    buildType.removeBuildConfigField(new BuildConfigFieldImpl("abcd", "efgh", "ijkl"));
    buildType.removeConsumerProguardFile("proguard-rules.pro");
    buildType.removeProguardFile("proguard-rules.pro");
    buildType.removeResValue(new ResValueImpl("opqr", "stuv", "wxyz"));
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    applyChanges(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("buildConfigFields", ImmutableList.of(new BuildConfigFieldImpl("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    assertEquals("resValues", ImmutableList.of(new ResValueImpl("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  public void testRemoveFromAndApplyListElementsWithSingleElement() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      consumerProguardFiles 'proguard-android.txt'\n" +
                  "      proguardFiles = ['proguard-rules.pro']\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-rules.pro"), buildType.proguardFiles());

    buildType.removeConsumerProguardFile("proguard-android.txt");
    buildType.removeProguardFile("proguard-rules.pro");
    assertThat(buildType.consumerProguardFiles()).named("consumerProguardFiles").isEmpty();
    assertThat(buildType.proguardFiles()).named("proguardFiles").isEmpty();

    applyChanges(buildModel);
    assertThat(buildType.consumerProguardFiles()).named("consumerProguardFiles").isEmpty();
    assertThat(buildType.proguardFiles()).named("proguardFiles").isEmpty();

    buildModel.reparse();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertThat(android).isInstanceOf(AndroidModelImpl.class);
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(android.buildTypes()).isEmpty();
  }

  public void testSetAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders key1:\"value1\", key2:\"value2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());

    buildType.setManifestPlaceholder("key1", 12345);
    buildType.setManifestPlaceholder("key3", true);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());
  }

  public void testAddAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertNull("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.setManifestPlaceholder("activityLabel1", "newName1");
    buildType.setManifestPlaceholder("activityLabel2", "newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());
  }

  public void testRemoveAndApplyMapElements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    xyz {\n" +
                  "      manifestPlaceholders activityLabel1:\"defaultName1\", activityLabel2:\"defaultName2\"\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildType.removeManifestPlaceholder("activityLabel1");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @NotNull
  private static BuildTypeModel getXyzBuildType(GradleBuildModel buildModel) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    List<BuildTypeModel> buildTypeModels = android.buildTypes();
    assertThat(buildTypeModels).hasSize(1);

    BuildTypeModel buildType = buildTypeModels.get(0);
    assertEquals("name", "xyz", buildType.name());
    return buildType;
  }
}
