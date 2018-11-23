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

import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_ADD_NON_EXISTED_LANGUAGE_LEVEL;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_ADD_NON_EXISTED_TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_DELETE_LANGUAGE_LEVEL;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSIONS_AS_DOUBLE_QUOTE_STRINGS;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSIONS_AS_NUMBERS;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSIONS_AS_QUALIFIED_REFERENCE_STRING;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSIONS_AS_REFERENCE_STRING;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSIONS_AS_SINGLE_QUOTE_STRINGS;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSION_LITERAL_FROM_EXT_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_READ_JAVA_VERSION_REFERENCE_FROM_EXT_PROPERTY;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_RESET_TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.TestFileName.JAVA_MODEL_SET_SOURCE_COMPATIBILITY;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import java.io.IOException;
import org.junit.Test;

/**
 * Tests for {@link JavaModelImpl}
 */
public class JavaModelTest extends GradleFileModelTestCase {
  @Test
  public void testReadJavaVersionsAsNumbers() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSIONS_AS_NUMBERS);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testReadJavaVersionsAsSingleQuoteStrings() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSIONS_AS_SINGLE_QUOTE_STRINGS);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testReadJavaVersionsAsDoubleQuoteStrings() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSIONS_AS_DOUBLE_QUOTE_STRINGS);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testReadJavaVersionsAsReferenceString() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSIONS_AS_REFERENCE_STRING);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testReadJavaVersionsAsQualifiedReferenceString() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSIONS_AS_QUALIFIED_REFERENCE_STRING);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testReadJavaVersionLiteralFromExtProperty() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSION_LITERAL_FROM_EXT_PROPERTY);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testReadJavaVersionReferenceFromExtProperty() throws IOException {
    writeToBuildFile(JAVA_MODEL_READ_JAVA_VERSION_REFERENCE_FROM_EXT_PROPERTY);
    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testSetSourceCompatibility() throws IOException {
    writeToBuildFile(JAVA_MODEL_SET_SOURCE_COMPATIBILITY);
    GradleBuildModel buildModel = getGradleBuildModel();
    JavaModel java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    java.sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_7);

    applyChangesAndReparse(buildModel);

    java = buildModel.java();
    assertEquals(LanguageLevel.JDK_1_7, java.sourceCompatibility().toLanguageLevel());
  }

  @Test
  public void testResetTargetCompatibility() throws IOException {
    writeToBuildFile(JAVA_MODEL_RESET_TARGET_COMPATIBILITY);
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
  @Test
  public void testAddNonExistedTargetCompatibility() throws IOException {
    writeToBuildFile(JAVA_MODEL_ADD_NON_EXISTED_TARGET_COMPATIBILITY);
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

  @Test
  public void testAddNonExistedLanguageLevel() throws Exception {
    writeToBuildFile(JAVA_MODEL_ADD_NON_EXISTED_LANGUAGE_LEVEL);

    GradleBuildModel buildModel = getGradleBuildModel();
    buildModel.java().sourceCompatibility().setLanguageLevel(LanguageLevel.JDK_1_5);

    applyChangesAndReparse(buildModel);

    assertEquals(LanguageLevel.JDK_1_5, buildModel.java().sourceCompatibility().toLanguageLevel());
  }

  @Test
  public void testDeleteLanguageLevel() throws Exception {
    writeToBuildFile(JAVA_MODEL_DELETE_LANGUAGE_LEVEL);
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
