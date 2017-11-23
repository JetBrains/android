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
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link SourceDirectoryModel}.
 */
public class SourceDirectoryModelTest extends GradleFileModelTestCase {
  private static final String GRADLE_TEXT = "android { \n" +
                                            "  sourceSets {\n" +
                                            "    main {\n" +
                                            "      java {\n" +
                                            "        srcDirs \"javaSource1\"\n" +
                                            "        srcDir \"javaSource2\"\n" +
                                            "        include \"javaInclude1\"\n" +
                                            "        include \"javaInclude2\"\n" +
                                            "        exclude \"javaExclude1\", \"javaExclude2\"\n" +
                                            "      }\n" +
                                            "      jni {\n" +
                                            "        srcDirs = [\"jniSource1\", \"jniSource2\"]\n" +
                                            "        include \"jniInclude1\", \"jniInclude2\"\n" +
                                            "        exclude \"jniExclude1\"\n" +
                                            "        exclude \"jniExclude2\"\n" +
                                            "      }\n" +
                                            "    }\n" +
                                            "  }\n" +
                                            "}";

  public void testSourceDirectoryEntries() throws Exception {
    writeToBuildFile(GRADLE_TEXT);
    verifySourceDirectoryEntries(getGradleBuildModel(), 1, 2);
  }


  public void testSourceDirectoryEntriesAddAndReset() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.addSrcDir("javaSource3");
    java.addInclude("javaInclude3");
    java.addExclude("javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.addSrcDir("jniSource3");
    jni.addInclude("jniInclude3");
    jni.addExclude("jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 2, 3);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  public void testSourceDirectoryEntriesAddAndApply() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.addSrcDir("javaSource3");
    java.addInclude("javaInclude3");
    java.addExclude("javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.addSrcDir("jniSource3");
    jni.addInclude("jniInclude3");
    jni.addExclude("jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 2, 3);

    applyChangesAndReparse(buildModel);
    verifySourceDirectoryEntries(buildModel, 1, 2, 3);
  }

  public void testSourceDirectoryEntriesRemoveAndReset() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.removeSrcDir("javaSource2");
    java.removeInclude("javaInclude2");
    java.removeExclude("javaExclude2");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.removeSrcDir("jniSource2");
    jni.removeInclude("jniInclude2");
    jni.removeExclude("jniExclude2");

    verifySourceDirectoryEntries(buildModel, 1);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  public void testSourceDirectoryEntriesRemoveAndApply() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.removeSrcDir("javaSource2");
    java.removeInclude("javaInclude2");
    java.removeExclude("javaExclude2");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.removeSrcDir("jniSource2");
    jni.removeInclude("jniInclude2");
    jni.removeExclude("jniExclude2");

    verifySourceDirectoryEntries(buildModel, 1);

    applyChangesAndReparse(buildModel);
    verifySourceDirectoryEntries(buildModel, 1);
  }

  public void testSourceDirectoryEntriesRemoveAllAndReset() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.removeAllSrcDirs();
    java.removeAllIncludes();
    java.removeAllExcludes();

    SourceDirectoryModel jni = sourceSet.jni();
    jni.removeAllSrcDirs();
    jni.removeAllIncludes();
    jni.removeAllExcludes();

    verifySourceDirectoryEntries(buildModel);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  public void testSourceDirectoryEntriesRemoveAllAndApply() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.removeAllSrcDirs();
    java.removeAllIncludes();
    java.removeAllExcludes();

    SourceDirectoryModel jni = sourceSet.jni();
    jni.removeAllSrcDirs();
    jni.removeAllIncludes();
    jni.removeAllExcludes();

    verifySourceDirectoryEntries(buildModel);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);
    checkForInValidPsiElement(android, AndroidModelImpl.class); // Whole android block gets removed as it would become empty.
    assertEmpty(android.sourceSets());
  }

  public void testSourceDirectoryEntriesReplaceAndReset() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.replaceSrcDir("javaSource2", "javaSource3");
    java.replaceInclude("javaInclude2", "javaInclude3");
    java.replaceExclude("javaExclude2", "javaExclude3");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.replaceSrcDir("jniSource2", "jniSource3");
    jni.replaceInclude("jniInclude2", "jniInclude3");
    jni.replaceExclude("jniExclude2", "jniExclude3");

    verifySourceDirectoryEntries(buildModel, 1, 3);

    buildModel.resetState();
    verifySourceDirectoryEntries(buildModel, 1, 2);
  }

  public void testSourceDirectoryEntriesReplaceAndApply() throws Exception {
    writeToBuildFile(GRADLE_TEXT);

    GradleBuildModel buildModel = getGradleBuildModel();
    verifySourceDirectoryEntries(buildModel, 1, 2);

    AndroidModel android = buildModel.android();
    assertNotNull(android);
    SourceSetModel sourceSet = android.sourceSets().get(0);

    SourceDirectoryModel java = sourceSet.java();
    java.replaceSrcDir("javaSource1", "javaSource0");
    java.replaceInclude("javaInclude1", "javaInclude0");
    java.replaceExclude("javaExclude1", "javaExclude0");

    SourceDirectoryModel jni = sourceSet.jni();
    jni.replaceSrcDir("jniSource1", "jniSource0");
    jni.replaceInclude("jniInclude1", "jniInclude0");
    jni.replaceExclude("jniExclude1", "jniExclude0");

    verifySourceDirectoryEntries(buildModel, 0, 2);

    applyChangesAndReparse(buildModel);
    verifySourceDirectoryEntries(buildModel, 0, 2);
  }

  private static void verifySourceDirectoryEntries(@NotNull GradleBuildModel buildModel, int... entrySuffixes) {
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    List<SourceSetModel> sourceSets = android.sourceSets();
    assertThat(sourceSets).hasSize(1);
    SourceSetModel sourceSet = sourceSets.get(0);
    assertEquals("name", "main", sourceSet.name());

    verifySourceDirectory(sourceSet.java(), "java", entrySuffixes);
    verifySourceDirectory(sourceSet.jni(), "jni", entrySuffixes);
  }

  private static void verifySourceDirectory(@NotNull SourceDirectoryModel sourceDirectory, @NotNull String name, int... entrySuffixes) {
    assertEquals("name", name, sourceDirectory.name());
    List<GradleNotNullValue<String>> srcDirs = sourceDirectory.srcDirs();
    List<GradleNotNullValue<String>> includes = sourceDirectory.includes();
    List<GradleNotNullValue<String>> excludes = sourceDirectory.excludes();

    if (entrySuffixes.length == 0) {
      assertNull(srcDirs);
      assertNull(includes);
      assertNull(excludes);
      return;
    }

    assertNotNull(srcDirs);
    assertNotNull(includes);
    assertNotNull(excludes);

    assertSize(entrySuffixes.length, srcDirs);
    assertSize(entrySuffixes.length, includes);
    assertSize(entrySuffixes.length, excludes);

    int i = 0;
    for (int entry : entrySuffixes) {
      assertEquals("srcDirs", name + "Source" + entry, srcDirs.get(i));
      assertEquals("includes", name + "Include" + entry, includes.get(i));
      assertEquals("excludes", name + "Exclude" + entry, excludes.get(i));
      i++;
    }
  }
}
