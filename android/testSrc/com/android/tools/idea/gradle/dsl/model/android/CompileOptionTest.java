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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.intellij.pom.java.LanguageLevel;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class CompileOptionTest extends GradleFileModelTestCase {
  public void testCompileOptionsBlock() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    encoding 'UTF8' \n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);


    CompileOptionsModel compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
    assertEquals("UTF8", compileOptions.encoding());
  }

  public void testCompileOptionsBlockUsingAssignment() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    encoding = 'UTF8' \n" +
                  "    sourceCompatibility = 1.6\n" +
                  "    targetCompatibility = 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
    assertEquals("UTF8", compileOptions.encoding());
  }

  public void testCompileOptionsApplicationStatement() throws Exception {
    String text = "android.compileOptions.sourceCompatibility 1.6\n" + "android.compileOptions.targetCompatibility 1.6\n";
    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

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
    assertNotNull(compileOptions);

    assertEquals(LanguageLevel.JDK_1_7, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.targetCompatibility());
  }

  public void testCompileOptionsRemoveBlock() throws Exception {
    String text = "android {\n" +
                  "  compileSdkVersion 23\n" +
                  "  compileOptions {\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNotNull(compileOptions);
    android.removeCompileOptions();

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    android = buildModel.android();

    assertNotNull(android);
    assertNull(android.compileOptions());
  }

  public void testCompileOptionsRemoveApplicationStatement() throws Exception {
    String text = "android {\n" +
                  "  compileSdkVersion 23\n" +
                  "  compileOptions {\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

    compileOptions.removeSourceCompatibility();
    compileOptions.removeTargetCompatibility();

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    android = buildModel.android();

    assertNotNull(android);
    compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

    assertNull(compileOptions.sourceCompatibility());
    assertNull(compileOptions.targetCompatibility());
  }

  public void testCompileOptionsModify() throws Exception {
    String text = "android {\n" +
                  "  compileOptions {\n" +
                  "    sourceCompatibility 1.6\n" +
                  "    targetCompatibility 1.6\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    compileOptions.setSourceCompatibility(LanguageLevel.JDK_1_7);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    android = buildModel.android();

    assertNotNull(android);
    compileOptions = android.compileOptions();
    assertNotNull(compileOptions);
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.sourceCompatibility());
  }

  public void testCompileOptionsAdd() throws Exception {
    String text = "android {\n" +
                  "  compileSdkVersion 23\n" +
                  "}";

    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CompileOptionsModel compileOptions = android.compileOptions();
    assertNull(compileOptions);

    android.addCompileOptions();
    compileOptions = android.compileOptions();
    assertNotNull(compileOptions);

    assertNull(compileOptions.sourceCompatibility());
    assertNull(compileOptions.targetCompatibility());

    compileOptions.setSourceCompatibility(LanguageLevel.JDK_1_6);
    compileOptions.setTargetCompatibility(LanguageLevel.JDK_1_7);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();
    android = buildModel.android();

    assertNotNull(android);
    compileOptions = android.compileOptions();
    assertNotNull(compileOptions);
    assertEquals(LanguageLevel.JDK_1_6, compileOptions.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_7, compileOptions.targetCompatibility());
  }
}