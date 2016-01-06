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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionDslElement;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;

import java.io.IOException;

import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.SOURCE_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.TARGET_COMPATIBILITY_ATTRIBUTE_NAME;

/**
 * Tests for {@link JavaModel}
 */
public class JavaModelTest extends GradleFileModelTestCase {
  public void testReadJavaVersionsAsNumbers() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.6";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());
  }

  public void testReadJavaVersionsAsSingleQuoteStrings() throws IOException {
    String text = "sourceCompatibility = \"1.5\"\n" +
                  "targetCompatibility = \"1.6\"";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());
  }

  public void testReadJavaVersionsAsDoubleQuoteStrings() throws IOException {
    String text = "sourceCompatibility = '1.5'\n" +
                  "targetCompatibility = '1.6'";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());
  }

  public void testReadJavaVersionsAsReferenceString() throws IOException {
    String text = "sourceCompatibility = VERSION_1_5\n" +
                  "targetCompatibility = VERSION_1_6";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());
  }

  public void testReadJavaVersionsAsQualifiedReferenceString() throws IOException {
    String text = "sourceCompatibility = JavaVersion.VERSION_1_5\n" +
                  "targetCompatibility = JavaVersion.VERSION_1_6";
    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());
  }

  public void testReadJavaVersionLiteralFromExtProperty() throws IOException {
    String text = "ext.JAVA_VERSION = 1.5\n" +
                  "sourceCompatibility = JAVA_VERSION\n" +
                  "targetCompatibility = JAVA_VERSION";

    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());
  }

  public void testReadJavaVersionReferenceFromExtProperty() throws IOException {
    String text = "ext.JAVA_VERSION = JavaVersion.VERSION_1_5\n" +
                  "sourceCompatibility = JAVA_VERSION\n" +
                  "targetCompatibility = JAVA_VERSION";

    writeToBuildFile(text);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());
  }

  public void testSetSourceCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    java.setSourceCompatibility(LanguageLevel.JDK_1_7);

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_7, java.sourceCompatibility());
  }

  public void testResetTargetCompatibility() throws IOException {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());

    java.setTargetCompatibility(LanguageLevel.JDK_1_7);
    buildModel.resetState();

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
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
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());

    assertNull(java.targetCompatibility());

    java.setTargetCompatibility(LanguageLevel.JDK_1_5);

    applyChangesAndReparse(buildModel);

    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility());

    JavaVersionDslElement targetVersionElement =
      java.getGradleDslElement().getProperty(TARGET_COMPATIBILITY_ATTRIBUTE_NAME, JavaVersionDslElement.class);
    JavaVersionDslElement sourceVersionElement =
      java.getGradleDslElement().getProperty(SOURCE_COMPATIBILITY_ATTRIBUTE_NAME, JavaVersionDslElement.class);
    assertNotNull(targetVersionElement);
    assertNotNull(sourceVersionElement);

    PsiElement targetPsi = targetVersionElement.getPsiElement();
    PsiElement sourcePsi = sourceVersionElement.getPsiElement();

    assertNotNull(targetPsi);
    assertNotNull(sourcePsi);

    // targetCompatibility should be previous to sourceCompatibility
    assertEquals(targetPsi, sourcePsi.getPrevSibling().getPrevSibling());
  }

  public void testAddNonExistedLanguageLevel() throws Exception {
    String text = "";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.java().setSourceCompatibility(LanguageLevel.JDK_1_5);

    applyChangesAndReparse(buildModel);

    assertEquals(LanguageLevel.JDK_1_5, buildModel.java().sourceCompatibility());
  }

  public void testDeleteLanguageLevel() throws Exception {
    String text = "sourceCompatibility = 1.5\n" +
                  "targetCompatibility = 1.5";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    java.removeSourceCompatibility();
    java.removeTargetCompatibility();

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
    assertNull(java.sourceCompatibility());
    assertNull(java.targetCompatibility());
  }
}
