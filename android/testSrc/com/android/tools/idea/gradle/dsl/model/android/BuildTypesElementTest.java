// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.BuildTypeModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link BuildTypesDslElement}.
 *
 * <p>
 * In this test, we only test the general structure of {@code android.buildTypes {}}. The build type structure defined by
 * {@link BuildTypeModelImpl} is tested in great deal to cover all combinations in {@link BuildTypeModelTest}.
 */
public class BuildTypesElementTest extends GradleFileModelTestCase {
  public void testBuildTypesWithApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    type1 {\n" +
                  "      applicationIdSuffix \"suffix1\"\n" +
                  "      proguardFiles 'proguard-android-1.txt', 'proguard-rules-1.txt'\n" +
                  "    }\n" +
                  "    type2 {\n" +
                  "      applicationIdSuffix \"suffix2\"\n" +
                  "      proguardFiles 'proguard-android-2.txt', 'proguard-rules-2.txt'\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);

    BuildTypeModel buildType1 = buildTypes.get(0);
    assertEquals("applicationIdSuffix", "suffix1", buildType1.applicationIdSuffix());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt"), buildType1.proguardFiles());
    BuildTypeModel buildType2 = buildTypes.get(1);
    assertEquals("applicationIdSuffix", "suffix2", buildType2.applicationIdSuffix());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt"), buildType2.proguardFiles());
  }

  public void testBuildTypesWithAssignmentStatements() throws Exception {
    String text = "android.buildTypes {\n" +
                  "  type1 {\n" +
                  "    applicationIdSuffix = \"suffix1\"\n" +
                  "    proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.txt']\n" +
                  "  }\n" +
                  "  type2 {\n" +
                  "    applicationIdSuffix = \"suffix2\"\n" +
                  "    proguardFiles = ['proguard-android-2.txt', 'proguard-rules-2.txt']\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertSize(2, buildTypes);

    BuildTypeModel buildType1 = buildTypes.get(0);
    assertEquals("applicationIdSuffix", "suffix1", buildType1.applicationIdSuffix());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt"), buildType1.proguardFiles());

    BuildTypeModel buildType2 = buildTypes.get(1);
    assertEquals("applicationIdSuffix", "suffix2", buildType2.applicationIdSuffix());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt"), buildType2.proguardFiles());
  }

  public void testBuildTypesWithOverrideStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    type1 {\n" +
                  "      applicationIdSuffix \"suffix1\"\n" +
                  "      proguardFiles 'proguard-android-1.txt', 'proguard-rules-1.txt'\n" +
                  "    }\n" +
                  "    type2 {\n" +
                  "      applicationIdSuffix = \"suffix2\"\n" +
                  "      proguardFiles 'proguard-android-2.txt', 'proguard-rules-2.txt'\n" +
                  "    }\n" +
                  "  }\n" +
                  "  buildTypes.type1 {\n" +
                  "    applicationIdSuffix = \"suffix1-1\"\n" +
                  "  }\n" +
                  "  buildTypes.type2 {\n" +
                  "    proguardFiles = ['proguard-android-4.txt', 'proguard-rules-4.txt']\n" +
                  "  }\n" +
                  " buildTypes {\n" +
                  "  type2.applicationIdSuffix = \"suffix2-1\"\n" +
                  " }\n" +
                  "}\n" +
                  "android.buildTypes.type1.proguardFiles = ['proguard-android-3.txt', 'proguard-rules-3.txt']\n";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);

    BuildTypeModel type1 = buildTypes.get(0);
    assertEquals("applicationIdSuffix", "suffix1-1", type1.applicationIdSuffix());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-3.txt", "proguard-rules-3.txt"), type1.proguardFiles());

    BuildTypeModel type2 = buildTypes.get(1);
    assertEquals("applicationIdSuffix", "suffix2-1", type2.applicationIdSuffix());
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-4.txt", "proguard-rules-4.txt"), type2.proguardFiles());
  }

  public void testBuildTypesWithAppendStatements() throws Exception {
    String text = "android {\n" +
                  "  buildTypes {\n" +
                  "    type1 {\n" +
                  "      proguardFiles = ['proguard-android-1.txt', 'proguard-rules-1.txt']\n" +
                  "    }\n" +
                  "    type2 {\n" +
                  "      proguardFiles 'proguard-android-2.txt', 'proguard-rules-2.txt'\n" +
                  "    }\n" +
                  "  }\n" +
                  "  buildTypes.type1 {\n" +
                  "    proguardFiles 'proguard-android-3.txt', 'proguard-rules-3.txt'\n" +
                  "  }\n" +
                  "  buildTypes.type2 {\n" +
                  "    testInstrumentationRunnerArguments.key6 \"value6\"\n" +
                  "  }\n" +
                  " buildTypes {\n" +
                  "  type2.proguardFile 'proguard-android-4.txt'\n" +
                  " }\n" +
                  "}\n";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    List<BuildTypeModel> buildTypes = android.buildTypes();
    assertThat(buildTypes).hasSize(2);

    BuildTypeModel type1 = buildTypes.get(0);
    assertEquals("proguardFiles",
                 ImmutableList.of("proguard-android-1.txt", "proguard-rules-1.txt", "proguard-android-3.txt", "proguard-rules-3.txt"),
                 type1.proguardFiles());

    BuildTypeModel type2 = buildTypes.get(1);
    assertEquals("proguardFiles", ImmutableList.of("proguard-android-2.txt", "proguard-rules-2.txt", "proguard-android-4.txt"),
                 type2.proguardFiles());
  }
}
