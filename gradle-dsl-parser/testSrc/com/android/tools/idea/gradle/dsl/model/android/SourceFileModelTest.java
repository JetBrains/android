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
import com.android.tools.idea.gradle.dsl.api.android.SourceSetModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link SourceFileModel}.
 */
public class SourceFileModelTest extends GradleFileModelTestCase {
  public void testSourceFile() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "        srcFile \"mainSource.xml\"\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    verifySourceFile(getGradleBuildModel(), "mainSource.xml");
  }

  public void testSourceFileEditAndReset() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "        srcFile \"mainSource.xml\"\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().setSrcFile("otherSource.xml");
    verifySourceFile(buildModel, "otherSource.xml");

    buildModel.resetState();
    verifySourceFile(buildModel, "mainSource.xml");
  }

  public void testSourceFileEditAndApply() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "        srcFile \"mainSource.xml\"\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().setSrcFile("otherSource.xml");
    verifySourceFile(buildModel, "otherSource.xml");

    applyChangesAndReparse(buildModel);
    verifySourceFile(buildModel, "otherSource.xml");
  }

  public void testSourceFileAddAndReset() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, null);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().setSrcFile("mainSource.xml");
    verifySourceFile(buildModel, "mainSource.xml");

    buildModel.resetState();
    verifySourceFile(buildModel, null);
  }

  public void testSourceFileAddAndApply() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, null);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().setSrcFile("mainSource.xml");
    verifySourceFile(buildModel, "mainSource.xml");

    applyChangesAndReparse(buildModel);
    verifySourceFile(buildModel, "mainSource.xml");
  }

  public void testSourceFileRemoveAndReset() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "        srcFile \"mainSource.xml\"\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().removeSrcFile();
    verifySourceFile(buildModel, null);

    buildModel.resetState();
    verifySourceFile(buildModel, "mainSource.xml");
  }

  public void testSourceFileRemoveAndApply() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      manifest {\n" +
                  "        srcFile \"mainSource.xml\"\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceFile(buildModel, "mainSource.xml");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    android.sourceSets().get(0).manifest().removeSrcFile();
    verifySourceFile(buildModel, null);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // Whole android block gets removed as it would become empty.
    assertThat(android.sourceSets()).isEmpty();
  }

  private static void verifySourceFile(@NotNull GradleBuildModel buildModel, @Nullable String srcFile) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());

    SourceFileModel manifest = sourceSet.manifest();
    assertNotNull(manifest);
    assertEquals("srcFile", srcFile, manifest.srcFile());
  }
}
