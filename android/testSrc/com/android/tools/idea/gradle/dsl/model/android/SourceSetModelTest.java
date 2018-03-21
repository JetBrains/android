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
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceFileModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link SourceSetModel}.
 */
public class SourceSetModelTest extends GradleFileModelTestCase {
  public void testSetRootInSourceSetBlock() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set1 {\n" +
                  "      root = \"source1\"\n" +
                  "    }\n" +
                  "    set2 {\n" +
                  "      root \"source2\"\n" +
                  "    }\n" +
                  "    set3 {\n" +
                  "      setRoot \"source3\"\n" +
                  "    }\n" +
                  "    set4 {\n" +
                  "      setRoot(\"source4\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);
    verifySourceSetRoot(getGradleBuildModel(), "source");
  }

  public void testSetRootStatements() throws Exception {
    String text = "android.sourceSets.set1.root = \"source1\"\n" +
                  "android.sourceSets.set2.root \"source2\"\n" +
                  "android.sourceSets.set3.setRoot \"source3\"\n" +
                  "android.sourceSets.set4.setRoot(\"source4\")";

    writeToBuildFile(text);
    verifySourceSetRoot(getGradleBuildModel(), "source");
  }

  public void testSetRootOverrideStatements() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set1 {\n" +
                  "      root = \"source1\"\n" +
                  "      setRoot \"override1\"\n" +
                  "    }\n" +
                  "    set2 {\n" +
                  "      root \"source2\"\n" +
                  "    }\n" +
                  "    set2.setRoot(\"override2\")\n" +
                  "    set3 {\n" +
                  "      setRoot \"source3\"\n" +
                  "    }\n" +
                  "    set4 {\n" +
                  "      setRoot(\"source4\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "  sourceSets.set3.root \"override3\"\n" +
                  "}\n" +
                  "android.sourceSets.set4.root = \"override4\"";

    writeToBuildFile(text);
    verifySourceSetRoot(getGradleBuildModel(), "override");
  }

  public void testSetRootEditAndReset() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set1 {\n" +
                  "      root = \"source1\"\n" +
                  "    }\n" +
                  "    set2.root \"source2\"\n" +
                  "  }\n" +
                  "  sourceSets.set3.setRoot \"source3\"\n" +
                  "}\n" +
                  "android.sourceSets.set4.setRoot(\"source4\")";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    sourceSets.get(0).setRoot("newRoot1");
    sourceSets.get(1).setRoot("newRoot2");
    sourceSets.get(2).setRoot("newRoot3");
    sourceSets.get(3).setRoot("newRoot4");
    verifySourceSetRoot(buildModel, "newRoot");

    buildModel.resetState();
    verifySourceSetRoot(buildModel, "source");
  }

  public void testSetRootEditAndApply() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set1 {\n" +
                  "      root = \"source1\"\n" +
                  "    }\n" +
                  "    set2.root \"source2\"\n" +
                  "  }\n" +
                  "  sourceSets.set3.setRoot \"source3\"\n" +
                  "}\n" +
                  "android.sourceSets.set4.setRoot(\"source4\")";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    sourceSets.get(0).setRoot("newRoot1");
    sourceSets.get(1).setRoot("newRoot2");
    sourceSets.get(2).setRoot("newRoot3");
    sourceSets.get(3).setRoot("newRoot4");
    verifySourceSetRoot(buildModel, "newRoot");

    applyChanges(buildModel);
    verifySourceSetRoot(buildModel, "newRoot");

    buildModel.reparse();
    verifySourceSetRoot(buildModel, "newRoot");
  }

  public void testSetRootAddAndReset() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);

    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "set", sourceSet.name());
    assertNull("root", sourceSet.root());

    sourceSet.setRoot("source");
    assertEquals("root", "source", sourceSet.root());

    buildModel.resetState();
    assertNull("root", sourceSet.root());
  }

  public void testSetRootAddAndApply() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}\n";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);

    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "set", sourceSet.name());
    assertNull("root", sourceSet.root());

    sourceSet.setRoot("source");
    assertEquals("root", "source", sourceSet.root());

    applyChanges(buildModel);
    assertEquals("root", "source", sourceSet.root());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);

    sourceSet = sourceSets.get(0);
    assertEquals("name", "set", sourceSet.name());
    assertEquals("root", "source", sourceSet.root());
  }

  public void testSetRootRemoveAndReset() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set1 {\n" +
                  "      root = \"source1\"\n" +
                  "    }\n" +
                  "    set2.root \"source2\"\n" +
                  "  }\n" +
                  "  sourceSets.set3.setRoot \"source3\"\n" +
                  "}\n" +
                  "android.sourceSets.set4.setRoot(\"source4\")";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);

    for (SourceSetModel sourceSet : android.sourceSets()) {
      sourceSet.removeRoot();
    }

    for (SourceSetModel sourceSet : android.sourceSets()) {
      assertNull("root", sourceSet.root());
    }

    buildModel.resetState();
    verifySourceSetRoot(buildModel, "source");
  }

  public void testSetRootRemoveAndApply() throws Exception {
    String text = "android {\n" +
                  "  sourceSets {\n" +
                  "    set1 {\n" +
                  "      root = \"source1\"\n" +
                  "    }\n" +
                  "    set2.root \"source2\"\n" +
                  "  }\n" +
                  "  sourceSets.set3.setRoot \"source3\"\n" +
                  "}\n" +
                  "android.sourceSets.set4.setRoot(\"source4\")";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceSetRoot(buildModel, "source");

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    for (SourceSetModel sourceSet : android.sourceSets()) {
      sourceSet.removeRoot();
    }

    for (SourceSetModel sourceSet : android.sourceSets()) {
      assertNull("root", sourceSet.root());
    }

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // the whole android block is deleted from the file.
    assertThat(android.sourceSets()).isEmpty();
  }

  private static void verifySourceSetRoot(@NotNull GradleBuildModel buildModel, @NotNull String rootPrefix) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(4);

    verifySourceSetRoot(sourceSets, rootPrefix);
  }

  private static void verifySourceSetRoot(@NotNull List<SourceSetModel> sourceSets, @NotNull String rootPrefix) {
    int i = 1;
    for (SourceSetModel sourceSet : sourceSets) {
      assertEquals("name", "set" + i, sourceSet.name());
      assertEquals("root", rootPrefix + i, sourceSet.root());
      i++;
    }
  }

  public void testAddAndApplyBlockElements() throws Exception {
    String text = "android { \n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());

    sourceSet.aidl().addSrcDir("aidlSource");
    sourceSet.assets().addSrcDir("assetsSource");
    sourceSet.java().addSrcDir("javaSource");
    sourceSet.jni().addSrcDir("jniSource");
    sourceSet.jniLibs().addSrcDir("jniLibsSource");
    sourceSet.manifest().setSrcFile("manifestSource.xml");
    sourceSet.renderscript().addSrcDir("renderscriptSource");
    sourceSet.res().addSrcDir("resSource");
    sourceSet.resources().addSrcDir("resourcesSource");
    verifySourceSet(sourceSet, false /*to verify that the block elements are still not saved to the file*/);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());
    verifySourceSet(sourceSet, true /*the elements are saved to file and the parser was able to find them all*/);
  }

  private static void verifySourceSet(SourceSetModel sourceSet, boolean savedToFile) {
    SourceDirectoryModel aidl = sourceSet.aidl();
    assertEquals("name", "aidl", aidl.name());
    assertThat(aidl.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(aidl));

    SourceDirectoryModel assets = sourceSet.assets();
    assertEquals("name", "assets", assets.name());
    assertThat(assets.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(aidl));

    SourceDirectoryModel java = sourceSet.java();
    assertEquals("name", "java", java.name());
    assertThat(java.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(java));

    SourceDirectoryModel jni = sourceSet.jni();
    assertEquals("name", "jni", jni.name());
    assertThat(jni.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(java));

    SourceDirectoryModel jniLibs = sourceSet.jniLibs();
    assertEquals("name", "jniLibs", jniLibs.name());
    assertThat(jniLibs.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(jniLibs));

    SourceFileModel manifest = sourceSet.manifest();
    assertEquals("name", "manifest", manifest.name());
    assertNotNull(manifest.srcFile());
    assertEquals(savedToFile, hasPsiElement(manifest));

    SourceDirectoryModel renderscript = sourceSet.renderscript();
    assertEquals("name", "renderscript", renderscript.name());
    assertThat(renderscript.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(renderscript));

    SourceDirectoryModel res = sourceSet.res();
    assertEquals("name", "res", res.name());
    assertThat(res.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(res));

    SourceDirectoryModel resources = sourceSet.resources();
    assertEquals("name", "resources", resources.name());
    assertThat(resources.srcDirs()).hasSize(1);
    assertEquals(savedToFile, hasPsiElement(resources));
  }

  public void testRemoveAndApplyBlockElements() throws Exception {
    String text = "android { \n" +
                  "  sourceSets {\n" +
                  "    main {\n" +
                  "      aidl {\n" +
                  "        srcDir \"aidlSource\"\n" +
                  "      }\n" +
                  "      assets {\n" +
                  "        srcDir \"aseetsSource\"\n" +
                  "      }\n" +
                  "      java {\n" +
                  "        srcDir \"javaSource\"\n" +
                  "      }\n" +
                  "      jni {\n" +
                  "        srcDir \"jniSource\"\n" +
                  "      }\n" +
                  "      jniLibs {\n" +
                  "        srcDir \"jniLibsSource\"\n" +
                  "      }\n" +
                  "      manifest {\n" +
                  "        srcFile \"manifestSource.xml\"\n" +
                  "      }\n" +
                  "      renderscript {\n" +
                  "        srcDir \"renderscriptSource\"\n" +
                  "      }\n" +
                  "      res {\n" +
                  "        srcDir \"resSource\"\n" +
                  "      }\n" +
                  "      resources {\n" +
                  "        srcDir \"resourcesSource\"\n" +
                  "      }\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);
    checkForValidPsiElement(android, AndroidModelImpl.class);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());
    verifySourceSet(sourceSet, true /*elements are present in the file and the parser was able to find them all*/);

    sourceSet.removeAidl();
    sourceSet.removeAssets();
    sourceSet.removeJava();
    sourceSet.removeJni();
    sourceSet.removeJniLibs();
    sourceSet.removeManifest();
    sourceSet.removeRenderscript();
    sourceSet.removeRes();
    sourceSet.removeResources();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // Whole android block gets removed as it would become empty.
    assertEmpty(android.sourceSets());
  }
}
