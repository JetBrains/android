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
package com.android.tools.idea.gradle.dsl.model.build;

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.java.JavaModel;
import com.intellij.pom.java.LanguageLevel;

/**
 * Tests allprojects section of the build.gradle file.
 */
public class AllProjectsTest extends GradleFileModelTestCase {
  public void testAllProjectsSection() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "allprojects { \n" +
                            "  sourceCompatibility = 1.5\n" +
                            "  targetCompatibility = 1.6\n" +
                            "}";

    String subModuleText = "";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, subModuleJavaModel.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, subModuleJavaModel.targetCompatibility());
  }

  public void testOverrideWithAllProjectsSection() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "sourceCompatibility = 1.4 \n" +
                            "targetCompatibility = 1.5 \n" +
                            "allprojects { \n" +
                            "  sourceCompatibility = 1.5\n" +
                            "  targetCompatibility = 1.6\n" +
                            "}";

    String subModuleText = "";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility()); // 1_4 is overridden with 1_5
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility()); // 1_5 is overridden with 1_6

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, subModuleJavaModel.sourceCompatibility()); // Subproject got 1_5 from allprojects section
    assertEquals(LanguageLevel.JDK_1_6, subModuleJavaModel.targetCompatibility()); // Subproject got 1_5 from allprojects section
  }

  public void testOverrideAllProjectsSection() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "allprojects { \n" +
                            "  sourceCompatibility = 1.4\n" +
                            "  targetCompatibility = 1.5\n" +
                            "}\n" +
                            "sourceCompatibility = 1.5 \n" +
                            "targetCompatibility = 1.6";

    String subModuleText = "";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility()); // 1_4 is overridden with 1_5
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility()); // 1_5 is overridden with 1_6

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_4, subModuleJavaModel.sourceCompatibility()); // Subproject got 1_4 from allprojects section
    assertEquals(LanguageLevel.JDK_1_5, subModuleJavaModel.targetCompatibility()); // Subproject got 1_5 from allprojects section
  }

  public void testOverrideAllProjectsSectionInSubproject() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "allprojects { \n" +
                            "  sourceCompatibility = 1.4\n" +
                            "  targetCompatibility = 1.5\n" +
                            "}\n" +
                            "sourceCompatibility = 1.5 \n" +
                            "targetCompatibility = 1.6";

    String subModuleText = "sourceCompatibility = 1.6\n" +
                           "targetCompatibility = 1.7";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility()); // 1_4 is overridden with 1_5
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility()); // 1_5 is overridden with 1_6

    JavaModel subModuleJavaModel = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_6, subModuleJavaModel.sourceCompatibility()); // 1_4 is overridden with 1_6
    assertEquals(LanguageLevel.JDK_1_7, subModuleJavaModel.targetCompatibility()); // 1_5 is overridden with 1_7
  }
}
