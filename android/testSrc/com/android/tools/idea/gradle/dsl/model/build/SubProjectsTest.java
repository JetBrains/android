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
 * Tests subprojects section of the build.gradle file.
 */
public class SubProjectsTest extends GradleFileModelTestCase {
  public void testSubProjectsSection() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "subprojects { \n" +
                            "  sourceCompatibility = 1.5\n" +
                            "  targetCompatibility = 1.6\n" +
                            "}";

    String subModuleText = "";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    JavaModel java = getGradleBuildModel().java();
    assertNull(java.sourceCompatibility());
    assertNull(java.targetCompatibility());

    JavaModel subModuleJava = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, subModuleJava.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, subModuleJava.targetCompatibility());
  }

  public void testSubProjectsSectionWithLocalProperties() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "sourceCompatibility = 1.4 \n" +
                            "targetCompatibility = 1.5 \n" +
                            "subprojects { \n" +
                            "  sourceCompatibility = 1.5\n" +
                            "  targetCompatibility = 1.6\n" +
                            "}";

    String subModuleText = "";

    writeToSettingsFile(settingsText);
    writeToBuildFile(mainModuleText);
    writeToSubModuleBuildFile(subModuleText);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_4, java.sourceCompatibility()); // subprojects section applies only for sub projects.
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility()); // subprojects section applies only for sub projects.

    JavaModel subModuleJava = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, subModuleJava.sourceCompatibility()); // Subproject got 1_5 from SubProjects section
    assertEquals(LanguageLevel.JDK_1_6, subModuleJava.targetCompatibility()); // Subproject got 1_6 from SubProjects section
  }

  public void testOverrideSubProjectsSection() throws Exception {
    String settingsText = "include ':" + SUB_MODULE_NAME + "'";

    String mainModuleText = "subprojects { \n" +
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
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility());

    JavaModel subModuleJava = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_6, subModuleJava.sourceCompatibility()); // 1_4 is overridden with 1_6
    assertEquals(LanguageLevel.JDK_1_7, subModuleJava.targetCompatibility()); // 1_5 is overridden with 1_7
  }
}
