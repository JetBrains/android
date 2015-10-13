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
package com.android.tools.idea.gradle.dsl;

import com.android.tools.idea.gradle.dsl.parser.GradleBuildModelParserTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;

import java.io.IOException;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class JavaElementTest extends GradleBuildModelParserTestCase {
  public void testSetSourceCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    final JavaElement javaElement = buildModel.java();
    assertNotNull(javaElement);

    assertEquals(LanguageLevel.JDK_1_5, javaElement.sourceCompatibility());
    javaElement.setSourceCompatibility(LanguageLevel.JDK_1_7);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        javaElement.applyChanges();
      }
    });
    buildModel.reparse();

    JavaElement newJavaProject = buildModel.java();
    assertNotNull(newJavaProject);

    assertEquals(LanguageLevel.JDK_1_7, newJavaProject.sourceCompatibility());
  }

  public void testResetTargetCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    final JavaElement javaElement = buildModel.java();
    assertNotNull(javaElement);

    assertEquals(LanguageLevel.JDK_1_5, javaElement.targetCompatibility());

    javaElement.setTargetCompatibility(LanguageLevel.JDK_1_7);
    javaElement.reset();

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        javaElement.applyChanges();
      }
    });
    buildModel.reparse();

    // Because of the reset, it should remain unchanged
    assertEquals(LanguageLevel.JDK_1_5, javaElement.targetCompatibility());
  }

  /**
   * If sourceCompatibility exists but targetCompatibility does not, check if newly added targetCompatibility has the right
   * default value and position.
   */
  public void testAddNonExistedTargetCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "dependencies {}\n";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    final JavaElement javaElement = buildModel.java();
    assertNotNull(javaElement);

    assertEquals(LanguageLevel.JDK_1_5, javaElement.sourceCompatibility());

    assertNull(javaElement.targetCompatibility());

    javaElement.setTargetCompatibility(LanguageLevel.JDK_1_5);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        javaElement.applyChanges();
      }
    });
    buildModel.reparse();

    assertEquals(LanguageLevel.JDK_1_5, javaElement.targetCompatibility());

    JavaVersionElement targetVersionElement = javaElement.getProperty(JavaElement.TARGET_COMPATIBILITY_FIELD, JavaVersionElement.class);
    JavaVersionElement sourceVersionElement = javaElement.getProperty(JavaElement.SOURCE_COMPATIBILITY_FIELD, JavaVersionElement.class);
    assertNotNull(targetVersionElement);
    assertNotNull(sourceVersionElement);

    PsiElement targetPsi = targetVersionElement.getPsiElement();
    PsiElement sourcePsi = sourceVersionElement.getPsiElement();

    assertNotNull(targetPsi);
    assertNotNull(sourcePsi);

    // targetCompatibility should be next to sourceCompatibility
    assertEquals(targetPsi.getParent(), sourcePsi.getParent().getNextSibling().getNextSibling());
  }

  public void testAddNonExistedLanguageLevel() throws Exception {
    String text = "";
    writeToBuildFile(text);

    final GradleBuildModel buildModel = getGradleBuildModel();
    JavaElement javaElement = buildModel.java();
    assertNull(javaElement);

    buildModel.addJavaElement();
    javaElement = buildModel.java();
    assertNotNull(javaElement);
    javaElement.setSourceCompatibility(LanguageLevel.JDK_1_5);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    javaElement = buildModel.java();
    assertNotNull(javaElement);

    assertEquals(LanguageLevel.JDK_1_5, javaElement.sourceCompatibility());
  }

  public void testDeleteLanguageLevel() throws Exception {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    final JavaElement javaElement = buildModel.java();
    assertNotNull(javaElement);
    javaElement.removeSourceCompatibility();
    javaElement.removeTargetCompatibility();

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        javaElement.applyChanges();
      }
    });
    buildModel.reparse();

    assertNull(javaElement.sourceCompatibility());
    assertNull(javaElement.targetCompatibility());
  }
}
