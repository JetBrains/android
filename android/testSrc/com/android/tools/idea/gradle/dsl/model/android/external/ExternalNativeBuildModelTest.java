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
package com.android.tools.idea.gradle.dsl.model.android.external;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;

import java.io.File;

/**
 * Tests for {@link ExternalNativeBuildModel}.
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

    ExternalNativeBuildModel externalNativeBuild = getGradleBuildModel().android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    CMakeModel cmake = externalNativeBuild.cmake();
    assertTrue(cmake.hasValidPsiElement());
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

    ExternalNativeBuildModel externalNativeBuild = getGradleBuildModel().android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    CMakeModel cmake = externalNativeBuild.cmake();
    assertTrue(cmake.hasValidPsiElement());
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
    ExternalNativeBuildModel externalNativeBuild = buildModel.android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertTrue(externalNativeBuild.cmake().hasValidPsiElement());

    externalNativeBuild.removeCMake();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertFalse(externalNativeBuild.cmake().hasValidPsiElement());

    buildModel.resetState();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertTrue(externalNativeBuild.cmake().hasValidPsiElement());
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
    ExternalNativeBuildModel externalNativeBuild = buildModel.android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertTrue(externalNativeBuild.cmake().hasValidPsiElement());

    externalNativeBuild.removeCMake();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertFalse(externalNativeBuild.cmake().hasValidPsiElement());

    applyChanges(buildModel);
    assertFalse(externalNativeBuild.hasValidPsiElement()); // Empty blocks are removed automatically.
    assertFalse(externalNativeBuild.cmake().hasValidPsiElement());

    buildModel.reparse();
    externalNativeBuild = buildModel.android().externalNativeBuild();
    assertFalse(externalNativeBuild.hasValidPsiElement());
    assertFalse(externalNativeBuild.cmake().hasValidPsiElement());
  }

  public void testAddCMakePathAndReset() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    ExternalNativeBuildModel externalNativeBuild = buildModel.android().externalNativeBuild();
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
    CMakeModel cmake = buildModel.android().externalNativeBuild().cmake();
    assertNull(cmake.path());

    cmake.setPath(new File("foo/bar"));
    assertEquals("path", new File("foo/bar"), cmake.path());

    applyChanges(buildModel);
    assertEquals("path", new File("foo/bar"), cmake.path());

    buildModel.reparse();
    cmake = buildModel.android().externalNativeBuild().cmake();
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

    ExternalNativeBuildModel externalNativeBuild = getGradleBuildModel().android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    assertTrue(ndkBuild.hasValidPsiElement());
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

    ExternalNativeBuildModel externalNativeBuild = getGradleBuildModel().android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    NdkBuildModel ndkBuild = externalNativeBuild.ndkBuild();
    assertTrue(ndkBuild.hasValidPsiElement());
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
    ExternalNativeBuildModel externalNativeBuild = buildModel.android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertTrue(externalNativeBuild.ndkBuild().hasValidPsiElement());

    externalNativeBuild.removeNdkBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertFalse(externalNativeBuild.ndkBuild().hasValidPsiElement());

    buildModel.resetState();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertTrue(externalNativeBuild.ndkBuild().hasValidPsiElement());
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
    ExternalNativeBuildModel externalNativeBuild = buildModel.android().externalNativeBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertTrue(externalNativeBuild.ndkBuild().hasValidPsiElement());

    externalNativeBuild.removeNdkBuild();
    assertTrue(externalNativeBuild.hasValidPsiElement());
    assertFalse(externalNativeBuild.ndkBuild().hasValidPsiElement());

    applyChanges(buildModel);
    assertFalse(externalNativeBuild.hasValidPsiElement()); // Empty blocks are removed automatically.
    assertFalse(externalNativeBuild.ndkBuild().hasValidPsiElement());

    buildModel.reparse();
    externalNativeBuild = buildModel.android().externalNativeBuild();
    assertFalse(externalNativeBuild.hasValidPsiElement());
    assertFalse(externalNativeBuild.ndkBuild().hasValidPsiElement());
  }

  public void testAddNdkBuildPathAndReset() throws Exception {
    String text = "android {\n" +
                  "}";

    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    NdkBuildModel ndkBuild = buildModel.android().externalNativeBuild().ndkBuild();
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
    NdkBuildModel ndkBuild = buildModel.android().externalNativeBuild().ndkBuild();
    assertNull(ndkBuild.path());

    ndkBuild.setPath(new File("foo/Android.mk"));
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());

    applyChanges(buildModel);
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());

    buildModel.reparse();
    ndkBuild = buildModel.android().externalNativeBuild().ndkBuild();
    assertEquals("path", new File("foo/Android.mk"), ndkBuild.path());
  }

}
