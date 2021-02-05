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

import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_ADD_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_ADD_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_EDIT_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_EDIT_ELEMENTS_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_EDIT_IGNORE_ASSET_PATTERN;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_EDIT_IGNORE_ASSET_PATTERN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_PARSE_ELEMENTS_ONE;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_PARSE_ELEMENTS_TWO;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_REMOVE_ELEMENTS;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_REMOVE_LAST_ELEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_REMOVE_ONE_ELEMENT;
import static com.android.tools.idea.gradle.dsl.TestFileNameImpl.ANDROID_RESOURCES_REMOVE_ONE_ELEMENT_EXPECTED;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidResourcesModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for {@link AndroidResourcesModel}.
 */
public class AndroidResourcesModelTest extends GradleFileModelTestCase {
  @Test
  public void testParseElementsOne() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_PARSE_ELEMENTS_ONE);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), androidResources.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, androidResources.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), androidResources.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, androidResources.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", androidResources.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), androidResources.noCompress());
  }

  @Test
  public void testParseElementsTwo() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_PARSE_ELEMENTS_TWO);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), androidResources.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.FALSE, androidResources.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(2), androidResources.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.TRUE, androidResources.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "ijkl", androidResources.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "b"), androidResources.noCompress());
  }

  @Test
  public void testEditElements() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_EDIT_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), androidResources.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.FALSE, androidResources.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(2), androidResources.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.TRUE, androidResources.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "ijkl", androidResources.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "b"), androidResources.noCompress());

    androidResources.additionalParameters().getListValue("efgh").setValue("xyz");
    androidResources.cruncherEnabled().setValue(true);
    androidResources.cruncherProcesses().setValue(3);
    androidResources.failOnMissingConfigEntry().setValue(false);
    androidResources.ignoreAssets().setValue("mnop");
    androidResources.noCompress().getListValue("b").setValue("c");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, ANDROID_RESOURCES_EDIT_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "xyz"), androidResources.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, androidResources.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(3), androidResources.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, androidResources.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "mnop", androidResources.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a", "c"), androidResources.noCompress());
  }

  @Test
  public void testEditIgnoreAssetPattern() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_EDIT_IGNORE_ASSET_PATTERN);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), androidResources.additionalParameters());
    assertEquals("ignoreAssets", "ijkl", androidResources.ignoreAssets());

    androidResources.ignoreAssets().setValue("mnop");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, ANDROID_RESOURCES_EDIT_IGNORE_ASSET_PATTERN_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    androidResources = android.androidResources();
    assertEquals("ignoreAssets", "mnop", androidResources.ignoreAssets());
  }

  @Test
  public void testAddElements() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_ADD_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    assertMissingProperty("additionalParameters", androidResources.additionalParameters());
    assertMissingProperty("cruncherEnabled", androidResources.cruncherEnabled());
    assertMissingProperty("cruncherProcesses", androidResources.cruncherProcesses());
    assertMissingProperty("failOnMissingConfigEntry", androidResources.failOnMissingConfigEntry());
    assertMissingProperty("ignoreAssets", androidResources.ignoreAssets());
    assertMissingProperty("noCompress", androidResources.noCompress());

    androidResources.additionalParameters().addListValue().setValue("abcd");
    androidResources.cruncherEnabled().setValue(true);
    androidResources.cruncherProcesses().setValue(1);
    androidResources.failOnMissingConfigEntry().setValue(false);
    androidResources.ignoreAssets().setValue("efgh");
    androidResources.noCompress().addListValue().setValue("a");

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, ANDROID_RESOURCES_ADD_ELEMENTS_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd"), androidResources.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, androidResources.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), androidResources.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, androidResources.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", androidResources.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), androidResources.noCompress());
  }

  @Test
  public void testRemoveElements() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_REMOVE_ELEMENTS);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    checkForValidPsiElement(androidResources, AndroidResourcesModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), androidResources.additionalParameters());
    assertEquals("cruncherEnabled", Boolean.TRUE, androidResources.cruncherEnabled());
    assertEquals("cruncherProcesses", Integer.valueOf(1), androidResources.cruncherProcesses());
    assertEquals("failOnMissingConfigEntry", Boolean.FALSE, androidResources.failOnMissingConfigEntry());
    assertEquals("ignoreAssets", "efgh", androidResources.ignoreAssets());
    assertEquals("noCompress", ImmutableList.of("a"), androidResources.noCompress());

    androidResources.additionalParameters().delete();
    androidResources.cruncherEnabled().delete();
    androidResources.cruncherProcesses().delete();
    androidResources.failOnMissingConfigEntry().delete();
    androidResources.ignoreAssets().delete();
    androidResources.noCompress().delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    androidResources = android.androidResources();
    checkForInvalidPsiElement(androidResources, AndroidResourcesModelImpl.class);
    assertMissingProperty("additionalParameters", androidResources.additionalParameters());
    assertMissingProperty("cruncherEnabled", androidResources.cruncherEnabled());
    assertMissingProperty("cruncherProcesses", androidResources.cruncherProcesses());
    assertMissingProperty("failOnMissingConfigEntry", androidResources.failOnMissingConfigEntry());
    assertMissingProperty("ignoreAssets", androidResources.ignoreAssets());
    assertMissingProperty("noCompress", androidResources.noCompress());
  }

  @Test
  public void testRemoveOneElementsInList() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_REMOVE_ONE_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("abcd", "efgh"), androidResources.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a", "b"), androidResources.noCompress());

    androidResources.additionalParameters().getListValue("abcd").delete();
    androidResources.noCompress().getListValue("b").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, ANDROID_RESOURCES_REMOVE_ONE_ELEMENT_EXPECTED);

    android = buildModel.android();
    assertNotNull(android);

    androidResources = android.androidResources();
    assertEquals("additionalParameters", ImmutableList.of("efgh"), androidResources.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a"), androidResources.noCompress());
  }

  @Test
  public void testRemoveLastElementInList() throws Exception {
    writeToBuildFile(ANDROID_RESOURCES_REMOVE_LAST_ELEMENT);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    AndroidResourcesModel androidResources = android.androidResources();
    checkForValidPsiElement(androidResources, AndroidResourcesModelImpl.class);
    assertEquals("additionalParameters", ImmutableList.of("abcd"), androidResources.additionalParameters());
    assertEquals("noCompress", ImmutableList.of("a"), androidResources.noCompress());

    androidResources.additionalParameters().getListValue("abcd").delete();
    androidResources.noCompress().getListValue("a").delete();

    applyChangesAndReparse(buildModel);
    verifyFileContents(myBuildFile, "");

    android = buildModel.android();
    assertNotNull(android);

    androidResources = android.androidResources();
    checkForInvalidPsiElement(androidResources, AndroidResourcesModelImpl.class);
    assertMissingProperty("additionalParameters", androidResources.additionalParameters());
    assertMissingProperty("noCompress", androidResources.noCompress());
  }
}
