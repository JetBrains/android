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
package com.android.tools.idea.gradle.dsl.model.java;

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionDslElement;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;

import java.io.IOException;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link JavaModel}
 */
public class JavaModelTest extends GradleFileModelTestCase {
  public void testSetSourceCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertNotNull(java);

    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    java.setSourceCompatibility(LanguageLevel.JDK_1_7);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    JavaModel newJavaProject = buildModel.java();
    assertNotNull(newJavaProject);

    assertEquals(LanguageLevel.JDK_1_7, newJavaProject.sourceCompatibility());
  }

  public void testResetTargetCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertNotNull(java);

    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());

    java.setTargetCompatibility(LanguageLevel.JDK_1_7);
    buildModel.resetState();

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    // Because of the reset, it should remain unchanged
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());
  }

  /**
   * If sourceCompatibility exists but targetCompatibility does not, check if newly added targetCompatibility has the right
   * default value and position.
   */
  public void testAddNonExistedTargetCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "dependencies {}\n";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertNotNull(java);

    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());

    assertNull(java.targetCompatibility());

    java.setTargetCompatibility(LanguageLevel.JDK_1_5);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());

    JavaVersionDslElement targetVersionElement =
      java.getGradleDslElement().getProperty(JavaModel.TARGET_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
    JavaVersionDslElement sourceVersionElement =
      java.getGradleDslElement().getProperty(JavaModel.SOURCE_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
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
    JavaModel java = buildModel.java();
    assertNull(java);

    buildModel.addJavaModel();
    java = buildModel.java();
    assertNotNull(java);
    java.setSourceCompatibility(LanguageLevel.JDK_1_5);

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    java = buildModel.java();
    assertNotNull(java);

    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
  }

  public void testDeleteLanguageLevel() throws Exception {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    final GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertNotNull(java);
    java.removeSourceCompatibility();
    java.removeTargetCompatibility();

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        buildModel.applyChanges();
      }
    });
    buildModel.reparse();

    assertNull(java.sourceCompatibility());
    assertNull(java.targetCompatibility());
  }
}
