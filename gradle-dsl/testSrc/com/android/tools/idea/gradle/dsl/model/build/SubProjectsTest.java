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

import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemDependent;
import org.junit.Test;


/**
 * Tests subprojects section of the build.gradle file.
 */
public class SubProjectsTest extends GradleFileModelTestCase {
  @Test
  public void testSubProjectsSection() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(TestFile.SUB_PROJECTS_SECTION);
    writeToSubModuleBuildFile("");

    JavaModel java = getGradleBuildModel().java();
    assertMissingProperty(java.sourceCompatibility());
    assertMissingProperty(java.targetCompatibility());

    JavaModel subModuleJava = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, subModuleJava.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, subModuleJava.targetCompatibility().toLanguageLevel());
  }

  @Test
  public void testSubProjectsSectionWithLocalProperties() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(TestFile.SUB_PROJECTS_SECTION_WITH_LOCAL_PROPERTIES);
    writeToSubModuleBuildFile("");

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_4, java.sourceCompatibility().toLanguageLevel()); // subprojects section applies only for sub projects.
    assertEquals(LanguageLevel.JDK_1_5, java.targetCompatibility().toLanguageLevel()); // subprojects section applies only for sub projects.

    JavaModel subModuleJava = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5,
                 subModuleJava.sourceCompatibility().toLanguageLevel()); // Subproject got 1_5 from SubProjects section
    assertEquals(LanguageLevel.JDK_1_6,
                 subModuleJava.targetCompatibility().toLanguageLevel()); // Subproject got 1_6 from SubProjects section
  }

  @Test
  public void testOverrideSubProjectsSection() throws Exception {
    writeToSettingsFile(getSubModuleSettingsText());
    writeToBuildFile(TestFile.OVERRIDE_SUB_PROJECT_SECTION);
    writeToSubModuleBuildFile(TestFile.OVERRIDE_SUB_PROJECT_SECTION_SUB);

    JavaModel java = getGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_5, java.sourceCompatibility().toLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, java.targetCompatibility().toLanguageLevel());

    JavaModel subModuleJava = getSubModuleGradleBuildModel().java();
    assertEquals(LanguageLevel.JDK_1_6, subModuleJava.sourceCompatibility().toLanguageLevel()); // 1_4 is overridden with 1_6
    assertEquals(LanguageLevel.JDK_1_7, subModuleJava.targetCompatibility().toLanguageLevel()); // 1_5 is overridden with 1_7
  }

  @Test
  public void testApplyPlugins() throws Exception {
    writeToBuildFile(TestFile.APPLY_PLUGINS);
    writeToSubModuleBuildFile(TestFile.APPLY_PLUGINS_SUB);
    Module otherSub = writeToNewSubModule("otherSub", TestFile.APPLY_PLUGINS_SUB2, "");
    writeToSettingsFile(getSubModuleSettingsText() + getSubModuleSettingsText("otherSub"));

    ProjectBuildModel buildModel = getProjectBuildModel();

    GradleBuildModel mainModel = buildModel.getModuleBuildModel(myModule);
    List<PluginModel> mainPlugins = mainModel.plugins();
    GradleBuildModel subModel = buildModel.getModuleBuildModel(mySubModule);
    List<PluginModel> subPlugins = subModel.plugins();
    GradleBuildModel sub2Model = buildModel.getModuleBuildModel(otherSub);
    List<PluginModel> sub2Plugins = sub2Model.plugins();

    assertSameElements(PluginModel.extractNames(mainPlugins), ImmutableSet.of("foo"));
    assertSameElements(PluginModel.extractNames(subPlugins), ImmutableSet.of("bar", "baz"));
    assertSameElements(PluginModel.extractNames(sub2Plugins), ImmutableSet.of("bar", "quux"));
  }

  enum TestFile implements TestFileName {
    APPLY_PLUGINS("applyPlugins"),
    APPLY_PLUGINS_SUB("applyPlugins_sub"),
    APPLY_PLUGINS_SUB2("applyPlugins_sub2"),
    OVERRIDE_SUB_PROJECT_SECTION("overrideSubProjectSection"),
    OVERRIDE_SUB_PROJECT_SECTION_SUB("overrideSubProjectSection_sub"),
    SUB_PROJECTS_SECTION("subProjectsSection"),
    SUB_PROJECTS_SECTION_WITH_LOCAL_PROPERTIES("subProjectsSectionWithLocalProperties"),
    ;

    @NotNull private @SystemDependent String path;
    TestFile(@NotNull @SystemDependent String path) {
      this.path = path;
    }

    @NotNull
    @Override
    public File toFile(@NotNull @SystemDependent String basePath, @NotNull String extension) {
      return TestFileName.super.toFile(basePath + "/subProjects/" + path, extension);
    }
  }
}
