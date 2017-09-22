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

import com.android.tools.idea.gradle.dsl.api.ExternalNativeBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.CMakeModel;
import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.NdkBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.CMakeModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.NdkBuildModelImpl;

import java.io.File;

/**
 * Tests for {@link ExternalNativeBuildModelImpl}.
 */
public class ExternalNativeBuildModelTest extends GradleFileModelTestCase {
  public void testCMake() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "      path file(\"foo/bar\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", new File("foo/bar"), cmake.path());
  }

  public void testCMakeWithNewFilePath() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "      path new File(\"foo/bar\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    CMakeModel cmake = externalNativeBuild.cmake();
    checkForValidPsiElement(cmake, CMakeModelImpl.class);
    assertEquals("path", new File("foo/bar"), cmake.path());
  }

  public void testRemoveCMakeAndReset() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    externalNativeBuild.removeCMake();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    buildModel.resetState();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);
  }

  public void testRemoveCMakeAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    cmake {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    externalNativeBuild.removeCMake();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    applyChanges(buildModel);
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class); // empty blocks removed
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    externalNativeBuild = android.externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.cmake(), CMakeModelImpl.class);
  }

  public void testAddCMakePathAndReset() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    CMakeModel cmake = externalNativeBuild.cmake();
    assertNull(cmake.path());

    cmake.setPath(new File("foo/bar"));
    assertEquals("path", new File("foo/bar"), cmake.path());

    buildModel.resetState();
    assertNull(cmake.path());
  }

  public void testAddCMakePathAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    CMakeModel cmake = android.externalNativeBuild().cmake();
    assertNull(cmake.path());

    cmake.setPath(new File("foo/bar"));
    assertEquals("path", new File("foo/bar"), cmake.path());

    applyChanges(buildModel);
    assertEquals("path", new File("foo/bar"), cmake.path());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    cmake = android.externalNativeBuild().cmake();
    assertEquals("path", new File("foo/bar"), cmake.path());
  }

  public void testNdkBuild() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "      path file(\"foo/Android.mk\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    checkForValidPsiElement(ndkBuild, NdkBuildModelImpl.class);
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());
  }

  public void testNdkBuildWithNewFilePath() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "      path new File(\"foo\", \"Android.mk\")\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    AndroidModel android = getGradleBuildModel().android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    checkForValidPsiElement(ndkBuild, NdkBuildModelImpl.class);
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());
  }

  public void testRemoveNdkBuildAndReset() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    externalNativeBuild.removeNdkBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    buildModel.resetState();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);
  }

  public void testRemoveNdkBuildAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "  externalNativeBuild {\n" +
                  "    ndkBuild {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    ExternalNativeBuildModel externalNativeBuild = android.externalNativeBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    externalNativeBuild.removeNdkBuild();
    checkForValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    applyChanges(buildModel);
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    externalNativeBuild = android.externalNativeBuild();
    checkForInValidPsiElement(externalNativeBuild, ExternalNativeBuildModelImpl.class);
    checkForInValidPsiElement(externalNativeBuild.ndkBuild(), NdkBuildModelImpl.class);
  }

  public void testAddNdkBuildPathAndReset() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuild = android.externalNativeBuild().ndkBuild();
    assertNull(ndkBuild.path());

    ndkBuild.setPath(new File("foo/Android.mk"));
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());

    buildModel.resetState();
    assertNull(ndkBuild.path());
  }

  public void testAddNdkBuildPathAndApplyChanges() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    AndroidModel android = buildModel.android();
    assertNotNull(android);

    NdkBuildModel ndkBuild = android.externalNativeBuild().ndkBuild();
    assertNull(ndkBuild.path());

    ndkBuild.setPath(new File("foo/Android.mk"));
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());

    applyChanges(buildModel);
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());

    buildModel.reparse();
    android = buildModel.android();
    assertNotNull(android);

    ndkBuild = android.externalNativeBuild().ndkBuild();
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());
  }
}
