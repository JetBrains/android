/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.kotlin;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.npw.NewProjectTestUtil;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;

import java.util.List;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateCppKotlinProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  FormFactor selectMobileTab = FormFactor.MOBILE;

  private List<String> expectedTemplates = List.of("Game Activity (C++)", "Native C++");
  private static final String NativeLibFilePath = "app/src/main/cpp/native-lib.cpp";
  private static final String CMakeListsFilePath = "app/src/main/cpp/CMakeLists.txt";
  private static final String DEFAULT_CMAKE_VERSION = "3.22.1";



  /**
   * <p>
   *   Verifies the IDE can create new projects with Kotlin and C++.
   * </p>
   *
   * <p>
   *   This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   *
   * <p>
   *   This test is a part of the test case with identifier
   *   4d4c36b0-23a7-4f16-9293-061e2fb1310f. This test was was too large, so it has
   *   been split into smaller automated tests. Please search for other usages of
   *   4d4c36b0-23a7-4f16-9293-061e2fb1310f to find other test cases that are a part of
   *   this test case.
   * </p>
   *
   * <p>
   *   The other known test cases of this test case are:
   *   <ul>
   *     <li>{@link BuildCppKotlinTest#buildCppKotlinProj}</li>
   *   </ul>
   * </p>
   *
   * <p>
   *   TT ID: 4d4c36b0-23a7-4f16-9293-061e2fb1310f
   * </p>
   *
   * <pre>
   *   Test Steps:
   *   1. Create new Project with C++ Native Activity and Kotlin as Source Language.
   *   2. Let Gradle Sync the Project. (Verify 1)
   *   3. Build project (Verify 2)
   *   Verify:
   *   1. Check if build is successful.
   *   2. C++ code is created
   * </pre>
   *
   * <p>
   *   This particular automated test just creates the project and verifies that the
   *   files of the project match the files of the pre-created project "CppKotlin".
   * </p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createNativeCppKotlinProject(){
    boolean buildProjectStatus = NewProjectTestUtil.createCppProject(guiTest, selectMobileTab, expectedTemplates.get(1), Language.Kotlin);
    assertThat(buildProjectStatus).isTrue();
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "gradle/libs.versions.toml",
      NativeLibFilePath,
      CMakeListsFilePath
    );
    String buildGradleContents = guiTest.getProjectFileText("app/build.gradle.kts");
    assertThat(buildGradleContents).contains("src/main/cpp/CMakeLists.txt");
    assertThat(buildGradleContents).contains("version = \""+DEFAULT_CMAKE_VERSION+"\"");

    validateNativLibFile();
    validateCMakeFileInNativeCPP();
  }

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createGameCppKotlinProject(){
    boolean buildProjectStatus = NewProjectTestUtil.createCppProject(guiTest, selectMobileTab, expectedTemplates.get(0), Language.Kotlin);
    assertThat(buildProjectStatus).isTrue();
    guiTest.ideFrame().getProjectView().assertFilesExist(
      "gradle/libs.versions.toml",
      CMakeListsFilePath
    );
    String buildGradleContents = guiTest.getProjectFileText("app/build.gradle.kts");
    assertThat(buildGradleContents).contains("src/main/cpp/CMakeLists.txt");
    assertThat(buildGradleContents).contains("version = \""+DEFAULT_CMAKE_VERSION+"\"");

    validateCMakeFileInGameCPP();
  }

  private void validateNativLibFile() {
    String nativeLibFile = guiTest.getProjectFileText(NativeLibFilePath);
    assertThat((nativeLibFile).contains("#include <jni.h>\n" +
                                             "#include <string>\n" +
                                             "\n" +
                                             "extern \"C\" JNIEXPORT jstring JNICALL\n" +
                                             "Java_com_example_myapplication_MainActivity_stringFromJNI(\n" +
                                             "        JNIEnv* env,\n" +
                                             "        jobject /* this */) {\n" +
                                             "    std::string hello = \"Hello from C++\";\n" +
                                             "    return env->NewStringUTF(hello.c_str());\n" +
                                             "}")).isTrue();
  }

  private void validateCMakeFileInNativeCPP() {
    String cMakeListsFile = guiTest.getProjectFileText(CMakeListsFilePath);
    assertThat((cMakeListsFile).contains("cmake_minimum_required(VERSION "+DEFAULT_CMAKE_VERSION+")")).isTrue();
    assertThat((cMakeListsFile).contains("add_library(${CMAKE_PROJECT_NAME} SHARED\n" +
                                              "        # List C/C++ source files with relative paths to this CMakeLists.txt.\n" +
                                              "        native-lib.cpp)")).isTrue();
  }

  private void validateCMakeFileInGameCPP() {
    String cMakeFileFile = guiTest.getProjectFileText(CMakeListsFilePath);
    assertThat((cMakeFileFile).contains("cmake_minimum_required(VERSION "+DEFAULT_CMAKE_VERSION+")")).isTrue();
    assertThat((cMakeFileFile).contains("add_library(myapplication SHARED\n" +
                                              "        main.cpp\n" +
                                              "        AndroidOut.cpp\n" +
                                              "        Renderer.cpp\n" +
                                              "        Shader.cpp\n" +
                                              "        TextureAsset.cpp\n" +
                                              "        Utility.cpp)")).isTrue();
  }
}
