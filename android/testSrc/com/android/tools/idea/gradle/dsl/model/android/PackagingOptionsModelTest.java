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
import com.android.tools.idea.gradle.dsl.api.android.PackagingOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link PackagingOptionsModel}.
 */
public class PackagingOptionsModelTest extends GradleFileModelTestCase {
  public void testParseElementsInApplicationStatements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    exclude 'exclude1'\n" +
                  "    excludes 'exclude2', 'exclude3'\n" +
                  "    merge 'merge1'\n" +
                  "    merges 'merge2', 'merge3'\n" +
                  "    pickFirst 'pickFirst1'\n" +
                  "    pickFirsts 'pickFirst2', 'pickFirst3'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  public void testParseElementsInAssignmentStatements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    excludes = ['exclude1', 'exclude2']\n" +
                  "    exclude 'exclude3'\n" +
                  "    merges = ['merge1', 'merge2']\n" +
                  "    merge 'merge3'\n" +
                  "    pickFirsts = ['pickFirst1', 'pickFirst2']\n" +
                  "    pickFirst 'pickFirst3'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());
  }

  public void testReplaceElements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    exclude 'exclude1'\n" +
                  "    excludes 'exclude2', 'exclude3'\n" +
                  "    merges = ['merge1', 'merge2']\n" +
                  "    merge 'merge3'\n" +
                  "    pickFirst 'pickFirst1'\n" +
                  "    pickFirsts 'pickFirst2', 'pickFirst3'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.replaceExclude("exclude1", "excludeX");
    packagingOptions.replaceMerge("merge2", "mergeX");
    packagingOptions.replacePickFirst("pickFirst3", "pickFirstX");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("excludeX", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "mergeX", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirstX"), packagingOptions.pickFirsts());
  }

  public void testAddElements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertNull("excludes", packagingOptions.excludes());
    assertNull("merges", packagingOptions.merges());
    assertNull("pickFirsts", packagingOptions.pickFirsts());

    packagingOptions.addExclude("exclude");
    packagingOptions.addMerge("merge");
    packagingOptions.addPickFirst("pickFirst");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst"), packagingOptions.pickFirsts());
  }

  public void testAppendElements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    exclude 'exclude1'\n" +
                  "    merges = ['merge1']\n" +
                  "    pickFirsts 'pickFirst1'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1"), packagingOptions.pickFirsts());

    packagingOptions.addExclude("exclude2");
    packagingOptions.addMerge("merge2");
    packagingOptions.addPickFirst("pickFirst2");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2"), packagingOptions.pickFirsts());
  }

  public void testRemoveElements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    exclude 'exclude1'\n" +
                  "    excludes 'exclude2', 'exclude3'\n" +
                  "    merges = ['merge1', 'merge2']\n" +
                  "    merge 'merge3'\n" +
                  "    pickFirst 'pickFirst1'\n" +
                  "    pickFirsts 'pickFirst2', 'pickFirst3'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    checkForValidPsiElement(packagingOptions, PackagingOptionsModelImpl.class);
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.removeAllExclude();
    packagingOptions.removeAllMerges();
    packagingOptions.removeAllPickFirsts();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertNull("excludes", packagingOptions.excludes());
    assertSize(0, packagingOptions.merges());
    assertNull("pickFirsts", packagingOptions.pickFirsts());
  }

  public void testRemoveOneOfElements() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    exclude 'exclude1'\n" +
                  "    excludes 'exclude2', 'exclude3'\n" +
                  "    merges = ['merge1', 'merge2']\n" +
                  "    merge 'merge3'\n" +
                  "    pickFirst 'pickFirst1'\n" +
                  "    pickFirsts 'pickFirst2', 'pickFirst3'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude1", "exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge2", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2", "pickFirst3"), packagingOptions.pickFirsts());

    packagingOptions.removeExclude("exclude1");
    packagingOptions.removeMerge("merge2");
    packagingOptions.removePickFirst("pickFirst3");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertEquals("excludes", ImmutableList.of("exclude2", "exclude3"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge1", "merge3"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst1", "pickFirst2"), packagingOptions.pickFirsts());
  }

  public void testRemoveOnlyElement() throws Exception {
    String text = "android {\n" +
                  "  packagingOptions {\n" +
                  "    exclude 'exclude'\n" +
                  "    merges = ['merge']\n" +
                  "    pickFirsts 'pickFirst'\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    PackagingOptionsModel packagingOptions = android.packagingOptions();
    checkForValidPsiElement(packagingOptions, PackagingOptionsModelImpl.class);
    assertEquals("excludes", ImmutableList.of("exclude"), packagingOptions.excludes());
    assertEquals("merges", ImmutableList.of("merge"), packagingOptions.merges());
    assertEquals("pickFirsts", ImmutableList.of("pickFirst"), packagingOptions.pickFirsts());

    packagingOptions.removeExclude("exclude");
    packagingOptions.removeMerge("merge");
    packagingOptions.removePickFirst("pickFirst");

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    packagingOptions = android.packagingOptions();
    assertNull("excludes", packagingOptions.excludes());
    assertSize(0, packagingOptions.merges());
    assertNull("pickFirsts", packagingOptions.pickFirsts());
  }
}
