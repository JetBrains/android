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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;

import java.io.IOException;

/**
 * Tests for {@link JavaModelImpl}
 */
public class JavaModelTest extends GradleFileModelTestCase {
  public void testReadJavaVersionsAsNumbers() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.6";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  public void testReadJavaVersionsAsSingleQuoteStrings() throws IOException {
    String text = "sourceCompatibility = \"1.5\"\n" +
                  "targetCompatibility = \"1.6\"";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  public void testReadJavaVersionsAsDoubleQuoteStrings() throws IOException {
    String text = "sourceCompatibility = '1.5'\n" +
                  "targetCompatibility = '1.6'";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  public void testReadJavaVersionsAsReferenceString() throws IOException {
    String text = "sourceCompatibility = VERSION_1_5\n" +
                  "targetCompatibility = VERSION_1_6";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  public void testReadJavaVersionsAsQualifiedReferenceString() throws IOException {
    String text = "sourceCompatibility = JavaVersion.VERSION_1_5\n" +
                  "targetCompatibility = JavaVersion.VERSION_1_6";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  public void testReadJavaVersionLiteralFromExtProperty() throws IOException {
    String text = "ext.JAVA_VERSION = 1.5\n" +
                  "sourceCompatibility = JAVA_VERSION\n" +
                  "targetCompatibility = JAVA_VERSION";

    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());
  }

  public void testReadJavaVersionReferenceFromExtProperty() throws IOException {
    String text = "ext.JAVA_VERSION = JavaVersion.VERSION_1_5\n" +
                  "sourceCompatibility = JAVA_VERSION\n" +
                  "targetCompatibility = JAVA_VERSION";

    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());
  }

  public void testSetSourceCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    java.sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7);

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_7, java.sourceCompatibility().toLanguageLevel());
  }

  public void testResetTargetCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());

    java.targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7);
    buildModel.resetState();

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
    // Because of the reset, it should remain unchanged
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());
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
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());

    assertMissingProperty(java.targetCompatibility());

    java.targetCompatibility().setLanguageLevel(LanguageLevel.JDK_1_5);

    applyChangesAndReparse(buildModel);

    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());

    PsiElement targetPsi = java.targetCompatibility().getPsiElement();
    PsiElement sourcePsi = java.sourceCompatibility().getPsiElement();

    assertNotNull(targetPsi);
    assertNotNull(sourcePsi);

    // targetCompatibility should be previous to sourceCompatibility
    // TODO: Enable proper placement of the written PsiElements.
    //assertEquals(targetPsi, sourcePsi.getPrevSibling().getPrevSibling());
  }

  public void testAddNonExistedLanguageLevel() throws Exception {
    String text = "";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.java().sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_5);

    applyChangesAndReparse(buildModel);

    assertEquals(LanguageLevel.JDK_1_5, buildModel.java().sourceCompatibility().toLanguageLevel());
  }

  public void testDeleteLanguageLevel() throws Exception {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    java.sourceCompatibility().delete();
    java.targetCompatibility().delete();

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
    assertMissingProperty(java.sourceCompatibility());
    assertMissingProperty(java.targetCompatibility());
  }
}
