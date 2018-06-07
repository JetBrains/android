/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tools.idea.gradle.dsl.api.android.CompileOptionsModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.intellij.pom.java.LanguageLevel;

public class CompileOptionsModelTest extends GradleFileModelTestCase {
  public void testCompileOptionsBlock() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    encoding 'UTF8'\n" +
                  "    incremental true\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
    assertEquals("UTF8", compileOptions.encoding());
    assertEquals(Boolean.TRUE, compileOptions.incremental());
  }

  public void testCompileOptionsBlockUsingAssignment() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    encoding = 'UTF8'\n" +
                  "    incremental = false\n" +
                  "    sourceCompatibility = 1.6\n" +
                  "    targetCompatibility = 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
    assertEquals("UTF8", compileOptions.encoding());
    assertEquals(Boolean.FALSE, compileOptions.incremental());
  }

  public void testCompileOptionsApplicationStatement() throws Exception {
    String text = "android.compileOptions.sourceCompatibility 1.6\n" + "android.compileOptions.targetCompatibility 1.6\n";
    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
  }

  // TODO test the case of remove sourceCompatibility with override
  public void testCompileOptionsBlockWithOverrideStatement() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "  }\n" +
                  "  compileOptions.sourceCompatibility 1.7\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
  }

  public void testCompileOptionsRemoveApplicationStatement() throws Exception {
    String text = "android {\n" +
                  "  compileSdkVersion 23\n" +
                  "  compileOptions {\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "    encoding 'UTF8'\n" +
                  "    incremental true\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    compileOptions.removeSourceCompatibility();
    compileOptions.removeTargetCompatibility();
    compileOptions.removeEncoding();
    compileOptions.removeIncremental();

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    checkForInValidPsiElement(compileOptions, CompileOptionsModelImpl.class);
    assertNull(compileOptions.sourceCompatibility());
    assertNull(compileOptions.targetCompatibility());
    assertNull(compileOptions.encoding());
    assertNull(compileOptions.incremental());
  }

  public void testCompileOptionsModify() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.7\n" +
                  "    encoding 'UTF8'\n" +
                  "    incremental false\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.targetCompatibility());
    assertEquals("UTF8", compileOptions.encoding());
    assertEquals(Boolean.FALSE, compileOptions.incremental());

    compileOptions.setSourceCompatibility(LanguageLevel.JDK_1_8);
    compileOptions.setTargetCompatibility(LanguageLevel.JDK_1_9);
    compileOptions.setEncoding("ISO-2022-JP");
    compileOptions.setIncremental(true);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_8, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_9, compileOptions.targetCompatibility());
    assertEquals("ISO-2022-JP", compileOptions.encoding());
    assertEquals(Boolean.TRUE, compileOptions.incremental());
  }

  public void testCompileOptionsAdd() throws Exception {
    String text = "android {\n" +
                  "  compileSdkVersion 23\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNull(compileOptions.sourceCompatibility());
    assertNull(compileOptions.targetCompatibility());
    assertNull(compileOptions.encoding());
    assertNull(compileOptions.incremental());

    compileOptions.setSourceCompatibility(LanguageLevel.JDK_1_6);
    compileOptions.setTargetCompatibility(LanguageLevel.JDK_1_7);
    compileOptions.setEncoding("UTF8");
    compileOptions.setIncremental(true);

    applyChangesAndReparse(buildModel);
    android = buildModel.android();
    assertNotNull(android);

    compileOptions = android.compileOptions();
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.targetCompatibility());
    assertEquals("UTF8", compileOptions.encoding());
    assertEquals(Boolean.TRUE, compileOptions.incremental());
  }
}