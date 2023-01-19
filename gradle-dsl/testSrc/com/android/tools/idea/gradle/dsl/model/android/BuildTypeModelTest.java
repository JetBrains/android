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

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.BOOLEAN_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.LIST_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.MAP_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.REFERENCE_TO_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.BOOLEAN;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.CUSTOM;
import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.STRING;
import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.api.ext.SigningConfigPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.semantics.AndroidGradlePluginVersion;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;

/**
 * Tests for {@link BuildTypeModelImpl}.
 */
public class BuildTypeModelTest extends GradleFileModelTestCase {
  @Test
  public void testBuildTypeBlockWithApplicationStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_BLOCK_WITH_APPLICATION_STATEMENTS);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
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
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeBlockWithAssignmentStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_BLOCK_WITH_ASSIGNMENT_STATEMENTS);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeApplicationStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_APPLICATION_STATEMENTS);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeAssignmentStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_ASSIGNMENT_STATEMENTS);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    // note that the Kotlin DSL name for this property is indeed multiDexEnabled (not isMultiDexEnabled)
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    if (isGroovy()) {
      // versions of AGP recent enough to have a Kotlin DSL have also removed the (deprecated in 3.0) Jack configuration
      assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    }
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeBlockWithOverrideStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_BLOCK_WITH_OVERRIDE_STATEMENTS);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("applicationIdSuffix", "mySuffix-3", buildType.applicationIdSuffix());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel3", "defaultName3", "activityLabel4", "defaultName4"),
                 buildType.manifestPlaceholders());
    assertEquals("multiDexEnabled", Boolean.FALSE, buildType.multiDexEnabled());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(2), buildType.renderscriptOptimLevel());
    assertEquals("versionNameSuffix", "abc-1", buildType.versionNameSuffix());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
    assertEquals("embedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals("jniDebuggable", Boolean.FALSE, buildType.jniDebuggable());
    assertEquals("minifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.pro"), buildType.proguardFiles());
    assertEquals("pseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals("renderscriptDebuggable", Boolean.FALSE, buildType.renderscriptDebuggable());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.FALSE, buildType.testCoverageEnabled());
    if (isGroovy()) {
      assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    }
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  @Test
  public void testBuildTypeBlockWithAppendStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_BLOCK_WITH_APPEND_STATEMENTS);

    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("manifestPlaceholders",
                 ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2", "activityLabel3", "defaultName3",
                                 "activityLabel4", "defaultName4"),
                 buildType.manifestPlaceholders());
    assertEquals("proguardFiles",
                 ImmutableList.of("pro-1.txt", "pro-2.txt", "pro-3.txt", "pro-4.txt", "pro-5.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues",
                     ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());
  }

  @Test
  public void testBuildTypeMapStatements() throws Exception {
    writeToBuildFile(TestFile.BUILD_TYPE_MAP_STATEMENTS);
    BuildTypeModel buildType = getXyzBuildType(getGradleBuildModel());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndResetElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_RESET_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().delete();
    buildType.removeAllBuildConfigFields();
    buildType.consumerProguardFiles().delete();
    buildType.crunchPngs().delete();
    buildType.debuggable().delete();
    buildType.isDefault().delete();
    buildType.embedMicroApp().delete();
    buildType.jniDebuggable().delete();
    buildType.manifestPlaceholders().delete();
    buildType.minifyEnabled().delete();
    buildType.multiDexEnabled().delete();
    buildType.proguardFiles().delete();
    buildType.pseudoLocalesEnabled().delete();
    buildType.renderscriptDebuggable().delete();
    buildType.renderscriptOptimLevel().delete();
    buildType.removeAllResValues();
    buildType.shrinkResources().delete();
    buildType.testCoverageEnabled().delete();
    buildType.useJack().delete();
    buildType.useProguard().delete();
    buildType.versionNameSuffix().delete();
    buildType.zipAlignEnabled().delete();
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildModel.resetState();
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals("shrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals("testCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testEditAndResetLiteralElements() throws Exception {
    writeToBuildFile(TestFile.EDIT_AND_RESET_LITERAL_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.crunchPngs().setValue(false);
    buildType.debuggable().setValue(false);
    buildType.isDefault().setValue(false);
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
    buildType.useProguard().setValue(false);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.resetState();
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());
  }

  @Test
  public void testAddAndResetLiteralElements() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_RESET_LITERAL_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.crunchPngs().setValue(false);
    buildType.debuggable().setValue(false);
    buildType.isDefault().setValue(false);
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
    buildType.useProguard().setValue(false);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.resetState();
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());
  }

  @Test
  public void testReplaceAndResetListElements() throws Exception {
    writeToBuildFile(TestFile.REPLACE_AND_RESET_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.replaceBuildConfigField("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst");
    replaceListValue(buildType.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    replaceListValue(buildType.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceResValue("mnop", "qrst", "uvwx", "mnop", "efgh", "ijkl");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());

    buildModel.resetState();
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testAddAndResetListElements() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_RESET_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertEmpty("resValues", buildType.resValues());

    buildType.addBuildConfigField("abcd", "efgh", "ijkl");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.addResValue("mnop", "qrst", "uvwx");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.resetState();
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertEmpty("resValues", buildType.resValues());
  }

  @Test
  public void testAddToAndResetListElements() throws Exception {
    writeToBuildFile(TestFile.ADD_TO_AND_RESET_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.addBuildConfigField("cdef", "ghij", "klmn");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.addResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());

    buildModel.resetState();
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testRemoveFromAndResetListElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_FROM_AND_RESET_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());

    buildType.removeBuildConfigField("abcd", "efgh", "ijkl");
    removeListValue(buildType.consumerProguardFiles(), "proguard-rules.pro");
    removeListValue(buildType.proguardFiles(), "proguard-rules.pro");
    buildType.removeResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.resetState();
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());
  }

  @Test
  public void testSetAndResetMapElements() throws Exception {
    writeToBuildFile(TestFile.SET_AND_RESET_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("key1").setValue(12345);
    buildType.manifestPlaceholders().getMapValue("key3").setValue(true);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());
  }

  @Test
  public void testAddAndResetMapElements() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_RESET_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1");
    buildType.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndResetMapElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_RESET_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getValue(MAP_TYPE).get("activityLabel1").delete();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildModel.resetState();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndApplyElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());

    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals(isGroovy()?"debuggable":"isDebuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
    assertEquals(isGroovy()?"embedMicroApp":"isEmbedMicroApp", Boolean.TRUE, buildType.embedMicroApp());
    assertEquals(isGroovy()?"jniDebuggable":"isJniDebuggable", Boolean.TRUE, buildType.jniDebuggable());
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
    assertEquals(isGroovy()?"minifyEnabled":"isMinifyEnabled", Boolean.TRUE, buildType.minifyEnabled());
    assertEquals("multiDexEnabled", Boolean.TRUE, buildType.multiDexEnabled());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    assertEquals(isGroovy()?"pseudoLocalesEnabled":"isPseudoLocalesEnabled", Boolean.TRUE, buildType.pseudoLocalesEnabled());
    assertEquals(isGroovy()?"renderscriptDebuggable":"isRenderscriptDebuggable", Boolean.TRUE, buildType.renderscriptDebuggable());
    assertEquals("renderscriptOptimLevel", Integer.valueOf(1), buildType.renderscriptOptimLevel());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
    assertEquals(isGroovy()?"shrinkResources":"isShrinkResources", Boolean.TRUE, buildType.shrinkResources());
    assertEquals(isGroovy()?"testCoverageEnabled":"isTestCoverageEnabled", Boolean.TRUE, buildType.testCoverageEnabled());
    assertEquals("useJack", Boolean.TRUE, buildType.useJack());
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals(isGroovy()?"zipAlignEnabled":"isZipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    // Remove all the properties except the applicationIdSuffix.
    buildType.removeAllBuildConfigFields();
    buildType.consumerProguardFiles().delete();
    buildType.crunchPngs().delete();
    buildType.debuggable().delete();
    buildType.isDefault().delete();
    buildType.embedMicroApp().delete();
    buildType.jniDebuggable().delete();
    buildType.manifestPlaceholders().delete();
    buildType.minifyEnabled().delete();
    buildType.multiDexEnabled().delete();
    buildType.proguardFiles().delete();
    buildType.pseudoLocalesEnabled().delete();
    buildType.renderscriptDebuggable().delete();
    buildType.renderscriptOptimLevel().delete();
    buildType.removeAllResValues();
    buildType.shrinkResources().delete();
    buildType.testCoverageEnabled().delete();
    buildType.useJack().delete();
    buildType.useProguard().delete();
    buildType.versionNameSuffix().delete();
    buildType.zipAlignEnabled().delete();
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_ELEMENTS_EXPECTED);

    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    buildType = getXyzBuildType(buildModel);
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    // Now remove the applicationIdSuffix and build type and see that the whole android block is removed as it would be an empty block.

    buildType.applicationIdSuffix().delete();
    buildModel.android().removeBuildType("xyz");
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertTrue(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertTrue(((BuildTypeModelImpl)buildType).hasValidPsiElement());
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, "");

    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertThat(buildType, instanceOf(BuildTypeModelImpl.class));
    assertFalse(((BuildTypeModelImpl)buildType).hasValidPsiElement());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
    assertFalse(((AndroidModelImpl)android).hasValidPsiElement());
    assertSize(2, android.buildTypes());
  }

  @Test
  public void testEditAndApplyLiteralElements() throws Exception {
    writeToBuildFile(TestFile.EDIT_AND_APPLY_LITERAL_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.TRUE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.TRUE, buildType.debuggable());
    assertEquals("default", Boolean.TRUE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.TRUE, buildType.useProguard());
    assertEquals("versionNameSuffix", "abc", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.TRUE, buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.crunchPngs().setValue(false);
    buildType.debuggable().setValue(false);
    buildType.isDefault().setValue(false);
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
    buildType.useProguard().setValue(false);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  @Test
  public void testAddAndApplyLiteralElements() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_LITERAL_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertMissingProperty("applicationIdSuffix", buildType.applicationIdSuffix());
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("crunchPngs", buildType.crunchPngs());
    assertMissingProperty("debuggable", buildType.debuggable());
    assertMissingProperty("default", buildType.isDefault());
    assertMissingProperty("embedMicroApp", buildType.embedMicroApp());
    assertMissingProperty("jniDebuggable", buildType.jniDebuggable());
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());
    assertMissingProperty("minifyEnabled", buildType.minifyEnabled());
    assertMissingProperty("multiDexEnabled", buildType.multiDexEnabled());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertMissingProperty("pseudoLocalesEnabled", buildType.pseudoLocalesEnabled());
    assertMissingProperty("renderscriptDebuggable", buildType.renderscriptDebuggable());
    assertMissingProperty("renderscriptOptimLevel", buildType.renderscriptOptimLevel());
    assertEmpty("resValues", buildType.resValues());
    assertMissingProperty("shrinkResources", buildType.shrinkResources());
    assertMissingProperty("testCoverageEnabled", buildType.testCoverageEnabled());
    assertMissingProperty("useJack", buildType.useJack());
    assertMissingProperty("useProguard", buildType.useProguard());
    assertMissingProperty("versionNameSuffix", buildType.versionNameSuffix());
    assertMissingProperty("zipAlignEnabled", buildType.zipAlignEnabled());

    buildType.applicationIdSuffix().setValue("mySuffix-1");
    buildType.crunchPngs().setValue(false);
    buildType.debuggable().setValue(false);
    buildType.isDefault().setValue(false);
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
    buildType.useProguard().setValue(false);
    buildType.versionNameSuffix().setValue("def");
    buildType.zipAlignEnabled().setValue(false);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED);

    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("applicationIdSuffix", "mySuffix-1", buildType.applicationIdSuffix());
    assertEquals("crunchPngs", Boolean.FALSE, buildType.crunchPngs());
    assertEquals("debuggable", Boolean.FALSE, buildType.debuggable());
    assertEquals("default", Boolean.FALSE, buildType.isDefault());
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
    assertEquals("useProguard", Boolean.FALSE, buildType.useProguard());
    assertEquals("versionNameSuffix", "def", buildType.versionNameSuffix());
    assertEquals("zipAlignEnabled", Boolean.FALSE, buildType.zipAlignEnabled());
  }

  @Test
  public void testReplaceAndApplyListElements() throws Exception {
    writeToBuildFile(TestFile.REPLACE_AND_APPLY_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.replaceBuildConfigField("abcd", "efgh", "ijkl", "abcd", "mnop", "qrst");
    replaceListValue(buildType.consumerProguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    replaceListValue(buildType.proguardFiles(), "proguard-android.txt", "proguard-android-1.txt");
    buildType.replaceResValue("mnop", "qrst", "uvwx", "mnop", "efgh", "ijkl");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED);

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "mnop", "qrst")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "efgh", "ijkl")), buildType.resValues());
  }

  @Test
  public void testAddAndApplyListElements() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEmpty("buildConfigFields", buildType.buildConfigFields());
    assertMissingProperty("consumerProguardFiles", buildType.consumerProguardFiles());
    assertMissingProperty("proguardFiles", buildType.proguardFiles());
    assertEmpty("resValues", buildType.resValues());

    buildType.addBuildConfigField("abcd", "efgh", "ijkl");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android.txt");
    buildType.addResValue("mnop", "qrst", "uvwx");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED);

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testAddToAndApplyListElements() throws Exception {
    writeToBuildFile(TestFile.ADD_TO_AND_APPLY_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildType.addBuildConfigField("cdef", "ghij", "klmn");
    buildType.consumerProguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.proguardFiles().addListValue().setValue("proguard-android-1.txt");
    buildType.addResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_TO_AND_APPLY_LIST_ELEMENTS_EXPECTED);

    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro", "proguard-android-1.txt"),
                 buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());
  }

  @Test
  public void testRemoveFromAndApplyListElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_FROM_AND_APPLY_LIST_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields",
                     ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl"), Lists.newArrayList("cdef", "ghij", "klmn")),
                     buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"),
                 buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt", "proguard-rules.pro"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx"), Lists.newArrayList("opqr", "stuv", "wxyz")),
                     buildType.resValues());

    buildType.removeBuildConfigField("abcd", "efgh", "ijkl");
    removeListValue(buildType.consumerProguardFiles(), "proguard-rules.pro");
    removeListValue(buildType.proguardFiles(), "proguard-rules.pro");
    buildType.removeResValue("opqr", "stuv", "wxyz");
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED);

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("cdef", "ghij", "klmn")), buildType.buildConfigFields());
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android.txt"), buildType.proguardFiles());
    verifyFlavorType("resValues", ImmutableList.of(Lists.newArrayList("mnop", "qrst", "uvwx")), buildType.resValues());
  }

  @Test
  public void testRemoveFromAndApplyListElementsWithSingleElement() throws Exception {
    // TODO(b/72853928): implementing setProguardFiles properly would allow us to do this test in Kotlin
    //  This test is groovy specific as it sets proguardFiles to a list which we cannot do in kotlin as it only accepts files parameters.
    //  There is support for setProguardFiles in AbstractFlavorTypeDslElement at the interpreter level: it correctly clears previous
    //  values stored there.  However, the model does not "know" that an empty setProguardFiles call has a side-effect; instead, we treat
    //  it as a statement with no effect, and remove it.
    assumeTrue("setProguardFiles parsing/model implementation insufficient in KotlinScript", !isKotlinScript());
    writeToBuildFile(TestFile.REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("consumerProguardFiles", ImmutableList.of("proguard-android.txt"), buildType.consumerProguardFiles());
    assertEquals("proguardFiles", ImmutableList.of("proguard-rules.pro"), buildType.proguardFiles());

    removeListValue(buildType.consumerProguardFiles(), "proguard-android.txt");
    removeListValue(buildType.proguardFiles(), "proguard-rules.pro");
    assertTrue(buildType.consumerProguardFiles().getValue(LIST_TYPE).isEmpty());
    assertTrue(buildType.proguardFiles().getValue(LIST_TYPE).isEmpty());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT_EXPECTED);

    assertTrue(buildType.consumerProguardFiles().getValue(LIST_TYPE).isEmpty());
    assertTrue(buildType.proguardFiles().getValue(LIST_TYPE).isEmpty());

    buildModel.reparse();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    assertThat(android, instanceOf(AndroidModelImpl.class));
  }

  @Test
  public void testSetAndApplyMapElements() throws Exception {
    writeToBuildFile(TestFile.SET_AND_APPLY_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", "value1", "key2", "value2"), buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("key1").setValue(12345);
    buildType.manifestPlaceholders().getMapValue("key3").setValue(true);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_AND_APPLY_MAP_ELEMENTS_EXPECTED);

    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("key1", 12345, "key2", "value2", "key3", true),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testAddAndApplyMapElements400() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.getContext().setAgpVersion(AndroidGradlePluginVersion.Companion.parse("4.0.0"));
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1");
    buildType.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_MAP_ELEMENTS_EXPECTED_400);

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testAddAndApplyMapElements() throws Exception {
    writeToBuildFile(TestFile.ADD_AND_APPLY_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyEmptyMapProperty("manifestPlaceholders", buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getMapValue("activityLabel1").setValue("newName1");
    buildType.manifestPlaceholders().getMapValue("activityLabel2").setValue("newName2");
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_MAP_ELEMENTS_EXPECTED);

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "newName1", "activityLabel2", "newName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testRemoveAndApplyMapElements() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_MAP_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel1", "defaultName1", "activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildType.manifestPlaceholders().getValue(MAP_TYPE).get("activityLabel1").delete();
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    applyChanges(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_MAP_ELEMENTS_EXPECTED);

    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());

    buildModel.reparse();
    buildType = getXyzBuildType(buildModel);
    assertEquals("manifestPlaceholders", ImmutableMap.of("activityLabel2", "defaultName2"),
                 buildType.manifestPlaceholders());
  }

  @Test
  public void testReadInitWith() throws IOException {
    writeToBuildFile(TestFile.OVERRIDE_WITH_INIT_WITH);

    List<BuildTypeModel> buildTypeModels = getGradleBuildModel().android().buildTypes();
    assertThat(buildTypeModels.size(), equalTo(4)); // 2 default + 2 custom

    BuildTypeModel fooBuildType = buildTypeModels.get(2);
    BuildTypeModel barBuildType = buildTypeModels.get(3);

    assertThat(fooBuildType.name(), equalTo("foo"));
    assertThat(barBuildType.name(), equalTo("bar"));

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), fooBuildType.buildConfigFields());
    assertThat(fooBuildType.minifyEnabled().toBoolean(), equalTo(true));
    assertThat(fooBuildType.debuggable().toBoolean(), equalTo(true));

    // Check if initWith is applied
    assertThat(barBuildType, instanceOf(BuildTypeModelImpl.class)); // We need to cast it, because initWith() is not part of the interface
    assertThat(((BuildTypeModelImpl)barBuildType).initWith().getRawValue(REFERENCE_TO_TYPE), equalTo(new ReferenceTo(fooBuildType)));

    verifyFlavorType("buildConfigFields", ImmutableList.of(Lists.newArrayList("abcd", "efgh", "ijkl")), barBuildType.buildConfigFields());
    assertThat(barBuildType.minifyEnabled().toBoolean(), equalTo(true));
    // Check if debuggable was overwritten back to false
    assertThat(barBuildType.debuggable().toBoolean(), equalTo(false));

    // Check that target buildType is not affected
    assertThat(fooBuildType.applicationIdSuffix().valueAsString(), equalTo(".foo"));
    assertThat(barBuildType.applicationIdSuffix().valueAsString(), equalTo(".bar"));
  }

  @Test
  public void testReadSigningConfig() throws Exception {
    writeToBuildFile(TestFile.READ_SIGNING_CONFIG);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE),
               isGroovy() ? equalTo("signingConfigs.myConfig") : equalTo("signingConfigs.getByName(\"myConfig\")"));
    SigningConfigModel signingConfigModel = buildType.signingConfig().toSigningConfig();
    assertThat(signingConfigModel.name(), equalTo("myConfig"));
  }

  @Test
  public void testSetSigningConfig() throws Exception {
    writeToBuildFile(TestFile.SET_SIGNING_CONFIG);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildTypeModel buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE),
               isGroovy()?equalTo("signingConfigs.myConfig"):equalTo("signingConfigs.getByName(\"myConfig\")"));
    SigningConfigPropertyModel signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myConfig"));
    // Set the value to be equal to a different config.
    List<SigningConfigModel> signingConfigs = buildModel.android().signingConfigs();
    assertThat(signingConfigs.size(), equalTo(3));
    assertThat(signingConfigs.get(1).name(), equalTo("myConfig"));
    assertThat(signingConfigs.get(2).name(), equalTo("myBetterConfig"));
    signingConfigModel.setValue(new ReferenceTo(signingConfigs.get(1)));
    assertThat(signingConfigs.size(), equalTo(3));
    assertThat(signingConfigs.get(1).name(), equalTo("myConfig"));
    assertThat(signingConfigs.get(2).name(), equalTo("myBetterConfig"));
    signingConfigModel.setValue(new ReferenceTo(signingConfigs.get(2)));

    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myBetterConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE),
               isGroovy()?equalTo("signingConfigs.myBetterConfig"):equalTo("signingConfigs.getByName(\"myBetterConfig\")"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myBetterConfig"));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_SIGNING_CONFIG_EXPECTED);

    signingConfigs = buildModel.android().signingConfigs();
    buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myBetterConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE),
               isGroovy()?equalTo("signingConfigs.myBetterConfig"):equalTo("signingConfigs.getByName(\"myBetterConfig\")"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myBetterConfig"));

    signingConfigModel.setValue(new ReferenceTo(signingConfigs.get(1)));
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE),
               isGroovy()?equalTo("signingConfigs.myConfig"):equalTo("signingConfigs.getByName(\"myConfig\")"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myConfig"));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_SIGNING_CONFIG);

    signingConfigModel.setValue(new ReferenceTo(signingConfigs.get(1)));

    buildType = getXyzBuildType(buildModel);
    verifyPropertyModel(buildType.signingConfig(), STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(buildType.signingConfig().getRawValue(STRING_TYPE),
               isGroovy()?equalTo("signingConfigs.myConfig"):equalTo("signingConfigs.getByName(\"myConfig\")"));
    signingConfigModel = buildType.signingConfig();
    assertThat(signingConfigModel.toSigningConfig().name(), equalTo("myConfig"));
  }

  @Test
  public void testSetSigningConfigFromEmpty() throws Exception {
    writeToBuildFile(TestFile.SET_SIGNING_CONFIG_FROM_EMPTY);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    BuildTypeModel buildTypeModel = android.addBuildType("xyz");

    SigningConfigModel signingConfig = android.signingConfigs().get(1);
    assertMissingProperty(buildTypeModel.signingConfig());
    buildTypeModel.signingConfig().setValue(new ReferenceTo(signingConfig));

    SigningConfigPropertyModel signingConfigPropertyModel = buildTypeModel.signingConfig();
    verifyPropertyModel(signingConfigPropertyModel, STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(signingConfigPropertyModel.getRawValue(STRING_TYPE),
               isGroovy()?equalTo("signingConfigs.myConfig"):equalTo("signingConfigs.getByName(\"myConfig\")"));
    assertThat(signingConfigPropertyModel.toSigningConfig().name(), equalTo("myConfig"));

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.SET_SIGNING_CONFIG_FROM_EMPTY_EXPECTED);

    android = buildModel.android();
    buildTypeModel = android.addBuildType("xyz");

    signingConfigPropertyModel = buildTypeModel.signingConfig();
    verifyPropertyModel(signingConfigPropertyModel, STRING_TYPE, "myConfig", CUSTOM, REGULAR, 1);
    assertThat(signingConfigPropertyModel.getRawValue(STRING_TYPE),
               isGroovy() ? equalTo("signingConfigs.myConfig") : equalTo("signingConfigs.getByName(\"myConfig\")"));
    assertThat(signingConfigPropertyModel.toSigningConfig().name(), equalTo("myConfig"));
  }

  @Test
  public void testRemoveAndApplyCreateBuildType() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_CREATE_BUILD_TYPE);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertSize(3, android.buildTypes());

    BuildTypeModel xyzModel = android.buildTypes().stream().filter(type -> type.name().equals("xyz")).findFirst().orElse(null);
    assertThat(xyzModel, is(notNullValue()));
    verifyPropertyModel(xyzModel.debuggable(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);
    BuildTypeModel releaseModel = android.buildTypes().stream().filter(type -> type.name().equals("release")).findFirst().orElse(null);
    assertThat(releaseModel, is(notNullValue()));
    // TODO(b/158066552): verifyPropertyModel(releaseModel.jniDebuggable(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 1);

    xyzModel.debuggable().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_CREATE_BUILD_TYPE_EXPECTED);
  }

  @Test
  public void testRemoveAndApplyGetByNameBuildType() throws Exception {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_GET_BY_NAME_BUILD_TYPE);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertSize(2, android.buildTypes());

    BuildTypeModel debugModel = android.buildTypes().stream().filter(type -> type.name().equals("debug")).findFirst().orElse(null);
    assertThat(debugModel, is(notNullValue()));
    BuildTypeModel releaseModel = android.buildTypes().stream().filter(type -> type.name().equals("release")).findFirst().orElse(null);
    assertThat(releaseModel, is(notNullValue()));

    debugModel.debuggable().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_GET_BY_NAME_BUILD_TYPE_EXPECTED);
  }

  // This test just makes sure its parsed correctly, if we decide to support these this test should be changed to
  // verify the correct behaviour.
  @Test
  public void testAllBuildTypes() throws Exception {
    writeToBuildFile(TestFile.ALL_BUILD_TYPES);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertSize(2, android.buildTypes());
    BuildTypeModel releaseBuildTypeModel = android.buildTypes().stream().filter(type -> type.name().equals("release")).findFirst().orElse(null);
    BuildTypeModel debugBuildTypeModel = android.buildTypes().stream().filter(type -> type.name().equals("debug")).findFirst().orElse(null);

    verifyPropertyModel(releaseBuildTypeModel.minifyEnabled(), BOOLEAN_TYPE, true, BOOLEAN, REGULAR, 0);
    verifyPropertyModel(releaseBuildTypeModel.applicationIdSuffix(), STRING_TYPE, "releaseSuffix", STRING, REGULAR, 0);

    verifyPropertyModel(debugBuildTypeModel.minifyEnabled(), BOOLEAN_TYPE, false, BOOLEAN, REGULAR, 0);
    verifyPropertyModel(debugBuildTypeModel.applicationIdSuffix(), STRING_TYPE, "debugSuffix", STRING, REGULAR, 0);
  }

  @Test
  public void renameImplicit() throws IOException {
    writeToBuildFile(TestFile.RENAME_IMPLICIT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();
    List<BuildTypeModel> buildTypes = androidModel.buildTypes();
    Truth.assertThat(buildTypes).hasSize(2);
    assertEquals("release", buildTypes.get(0).name());
    buildTypes.get(0).rename("newRelease");
    assertEquals("debug", buildTypes.get(1).name());
    buildTypes.get(1).rename("newDebug");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_IMPLICIT_EXPECTED);
  }

  @Test
  public void renameExplicit() throws IOException {
    writeToBuildFile(TestFile.RENAME_EXPLICIT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();
    List<BuildTypeModel> buildTypes = androidModel.buildTypes();
    Truth.assertThat(buildTypes).hasSize(2);
    assertEquals("release", buildTypes.get(0).name());
    buildTypes.get(0).rename("newRelease");
    assertEquals("debug", buildTypes.get(1).name());
    buildTypes.get(1).rename("newDebug");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_EXPLICIT_EXPECTED);
  }

  @Test
  public void renameToImplicit() throws IOException {
    writeToBuildFile(TestFile.RENAME_TO_IMPLICIT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();
    List<BuildTypeModel> buildTypes = androidModel.buildTypes();
    Truth.assertThat(buildTypes).hasSize(4);
    assertEquals("notRelease", buildTypes.get(2).name());
    buildTypes.get(2).rename("release");
    assertEquals("notDebug", buildTypes.get(3).name());
    buildTypes.get(3).rename("debug");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_TO_IMPLICIT_EXPECTED);
  }

  @Test
  public void renameExplicitToImplicit() throws IOException {
    writeToBuildFile(TestFile.RENAME_EXPLICIT_TO_IMPLICIT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel androidModel = buildModel.android();
    List<BuildTypeModel> buildTypes = androidModel.buildTypes();
    Truth.assertThat(buildTypes).hasSize(2);
    assertEquals("release", buildTypes.get(0).name());
    buildTypes.get(0).rename("debug");
    assertEquals("debug", buildTypes.get(1).name());
    buildTypes.get(1).rename("release");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, TestFile.RENAME_EXPLICIT_TO_IMPLICIT_EXPECTED);
  }

  @NotNull
  private static BuildTypeModel getXyzBuildType(GradleBuildModel buildModel) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    List<BuildTypeModel> buildTypeModels = android.buildTypes();
    assertThat(buildTypeModels.size(), equalTo(3));

    BuildTypeModel buildType = buildTypeModels.get(2);
    assertEquals("name", "xyz", buildType.name());
    return buildType;
  }

  enum TestFile implements TestFileName {
    BUILD_TYPE_BLOCK_WITH_APPLICATION_STATEMENTS("buildTypeBlockWithApplicationStatements"),
    BUILD_TYPE_BLOCK_WITH_ASSIGNMENT_STATEMENTS("buildTypeBlockWithAssignmentStatements"),
    BUILD_TYPE_APPLICATION_STATEMENTS("buildTypeApplicationStatements"),
    BUILD_TYPE_ASSIGNMENT_STATEMENTS("buildTypeAssignmentStatements"),
    BUILD_TYPE_BLOCK_WITH_OVERRIDE_STATEMENTS("buildTypeBlockWithOverrideStatements"),
    BUILD_TYPE_BLOCK_WITH_APPEND_STATEMENTS("buildTypeBlockWithAppendStatements"),
    BUILD_TYPE_MAP_STATEMENTS("buildTypeMapStatements"),
    REMOVE_AND_RESET_ELEMENTS("removeAndResetElements"),
    EDIT_AND_RESET_LITERAL_ELEMENTS("editAndResetLiteralElements"),
    ADD_AND_RESET_LITERAL_ELEMENTS("addAndResetLiteralElements"),
    REPLACE_AND_RESET_LIST_ELEMENTS("replaceAndResetListElements"),
    ADD_AND_RESET_LIST_ELEMENTS("addAndResetListElements"),
    ADD_TO_AND_RESET_LIST_ELEMENTS("addToAndResetListElements"),
    REMOVE_FROM_AND_RESET_LIST_ELEMENTS("removeFromAndResetListElements"),
    SET_AND_RESET_MAP_ELEMENTS("setAndResetMapElements"),
    ADD_AND_RESET_MAP_ELEMENTS("addAndResetMapElements"),
    REMOVE_AND_RESET_MAP_ELEMENTS("removeAndResetMapElements"),
    REMOVE_AND_APPLY_ELEMENTS("removeAndApplyElements"),
    REMOVE_AND_APPLY_ELEMENTS_EXPECTED("removeAndApplyElementsExpected"),
    EDIT_AND_APPLY_LITERAL_ELEMENTS("editAndApplyLiteralElements"),
    EDIT_AND_APPLY_LITERAL_ELEMENTS_EXPECTED("editAndApplyLiteralElementsExpected"),
    ADD_AND_APPLY_LITERAL_ELEMENTS("addAndApplyLiteralElements"),
    ADD_AND_APPLY_LITERAL_ELEMENTS_EXPECTED("addAndApplyLiteralElementsExpected"),
    REPLACE_AND_APPLY_LIST_ELEMENTS("replaceAndApplyListElements"),
    REPLACE_AND_APPLY_LIST_ELEMENTS_EXPECTED("replaceAndApplyListElementsExpected"),
    ADD_AND_APPLY_LIST_ELEMENTS("addAndApplyListElements"),
    ADD_AND_APPLY_LIST_ELEMENTS_EXPECTED("addAndApplyListElementsExpected"),
    ADD_TO_AND_APPLY_LIST_ELEMENTS("addToAndApplyListElements"),
    ADD_TO_AND_APPLY_LIST_ELEMENTS_EXPECTED("addToAndApplyListElementsExpected"),
    REMOVE_FROM_AND_APPLY_LIST_ELEMENTS("removeFromAndApplyListElements"),
    REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_EXPECTED("removeFromAndApplyListElementsExpected"),
    REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT("removeFromAndApplyListElementsWithSingleElement"),
    REMOVE_FROM_AND_APPLY_LIST_ELEMENTS_WITH_SINGLE_ELEMENT_EXPECTED("removeFromAndApplyListElementsWithSingleElementExpected"),
    SET_AND_APPLY_MAP_ELEMENTS("setAndApplyMapElements"),
    SET_AND_APPLY_MAP_ELEMENTS_EXPECTED("setAndApplyMapElementsExpected"),
    ADD_AND_APPLY_MAP_ELEMENTS("addAndApplyMapElements"),
    ADD_AND_APPLY_MAP_ELEMENTS_EXPECTED("addAndApplyMapElementsExpected"),
    ADD_AND_APPLY_MAP_ELEMENTS_EXPECTED_400("addAndApplyMapElementsExpected400"),
    REMOVE_AND_APPLY_CREATE_BUILD_TYPE("removeAndApplyCreateBuildType"),
    REMOVE_AND_APPLY_CREATE_BUILD_TYPE_EXPECTED("removeAndApplyCreateBuildTypeExpected"),
    REMOVE_AND_APPLY_GET_BY_NAME_BUILD_TYPE("removeAndApplyGetByNameBuildType"),
    REMOVE_AND_APPLY_GET_BY_NAME_BUILD_TYPE_EXPECTED("removeAndApplyGetByNameBuildTypeExpected"),
    REMOVE_AND_APPLY_MAP_ELEMENTS("removeAndApplyMapElements"),
    REMOVE_AND_APPLY_MAP_ELEMENTS_EXPECTED("removeAndApplyMapElementsExpected"),
    OVERRIDE_WITH_INIT_WITH("overrideWithInitWith"),
    READ_SIGNING_CONFIG("readSigningConfig"),
    SET_SIGNING_CONFIG("setSigningConfig"),
    SET_SIGNING_CONFIG_EXPECTED("setSigningConfigExpected"),
    SET_SIGNING_CONFIG_FROM_EMPTY("setSigningConfigFromEmpty"),
    SET_SIGNING_CONFIG_FROM_EMPTY_EXPECTED("setSigningConfigFromEmptyExpected"),
    ALL_BUILD_TYPES("allBuildTypes"),
    RENAME_IMPLICIT("renameImplicit"),
    RENAME_IMPLICIT_EXPECTED("renameImplicitExpected"),
    RENAME_EXPLICIT("renameExplicit"),
    RENAME_EXPLICIT_EXPECTED("renameExplicitExpected"),
    RENAME_TO_IMPLICIT("renameToImplicit"),
    RENAME_TO_IMPLICIT_EXPECTED("renameToImplicitExpected"),
    RENAME_EXPLICIT_TO_IMPLICIT("renameExplicitToImplicit"),
    RENAME_EXPLICIT_TO_IMPLICIT_EXPECTED("renameExplicitToImplicitExpected"),
    ;

    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/buildTypeModel/" + path, extension);
    }
  }
}
