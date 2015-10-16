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
package com.android.tools.idea.gradle.dsl.model;

import com.google.common.collect.ImmutableList;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * Tests for {@link GradleSettingsModel}.
 */
public class GradleSettingsModelTest extends GradleFileModelTestCase {
  public void testIncludedModulePaths() throws Exception {
    String text = "include \":app\" \n" +
                  "include \":lib\", \":lib:subLib\"";

    writeToSettingsFile(text);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());
  }

  public void testAddAndResetModulePaths() throws Exception {
    String text = "include \":app\", \":lib\"";

    writeToSettingsFile(text);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib"), settingsModel.modulePaths());

    settingsModel.addModulePath("lib1");
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableList.of(":app", ":lib"), settingsModel.modulePaths());
  }

  public void testAddAndApplyModulePaths() throws Exception {
    String text = "include \":app\", \":lib\"";

    writeToSettingsFile(text);
    final GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib"), settingsModel.modulePaths());

    settingsModel.addModulePath("lib1");
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib1"), settingsModel.modulePaths());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        settingsModel.applyChanges();
      }
    });
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib1"), settingsModel.modulePaths());
  }

  public void testAddAndApplyAllModulePaths() throws Exception {
    String text = "";

    writeToSettingsFile(text);
    final GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertNull(settingsModel.modulePaths());

    settingsModel.addModulePath("app");
    //settingsModel.addModulePath("lib");
    assertEquals("include", ImmutableList.of(":app"), settingsModel.modulePaths());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        settingsModel.applyChanges();
      }
    });
    assertEquals("include", ImmutableList.of(":app"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":app"), settingsModel.modulePaths());
  }

  public void testRemoveAndResetModulePaths() throws Exception {
    String text = "include \":app\" \n" +
                  "include \":lib\"";

    writeToSettingsFile(text);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    assertEquals("include", ImmutableList.of(":lib"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableList.of(":app", ":lib"), settingsModel.modulePaths());
  }

  public void testRemoveAndApplyModulePaths() throws Exception {
    String text = "include \":app\" \n" +
                  "include \":lib\"";

    writeToSettingsFile(text);
    final GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    assertEquals("include", ImmutableList.of(":lib"), settingsModel.modulePaths());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        settingsModel.applyChanges();
      }
    });
    assertEquals("include", ImmutableList.of(":lib"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":lib"), settingsModel.modulePaths());
  }

  public void testRemoveAndApplyAllModulePaths() throws Exception {
    String text = "include \":app\" \n" +
                  "include \":lib\", \":lib1\"";

    writeToSettingsFile(text);
    final GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib1"), settingsModel.modulePaths());

    settingsModel.removeModulePath(":app");
    settingsModel.removeModulePath("lib");
    settingsModel.removeModulePath(":lib1");
    assertEquals("include", ImmutableList.of(), settingsModel.modulePaths());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        settingsModel.applyChanges();
      }
    });
    assertEquals("include", ImmutableList.of(), settingsModel.modulePaths());

    settingsModel.reparse();
    assertNull(settingsModel.modulePaths());
  }

  public void testReplaceAndResetModulePaths() throws Exception {
    String text = "include \":app\" \n" +
                  "include \":lib\", \":lib:subLib\"";

    writeToSettingsFile(text);
    GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.replaceModulePath("lib", "lib1");
    assertEquals("include", ImmutableList.of(":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.resetState();
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());
  }

  public void testReplaceAndApplyModulePaths() throws Exception {
    String text = "include \":app\" \n" +
                  "include \":lib\", \":lib:subLib\"";

    writeToSettingsFile(text);
    final GradleSettingsModel settingsModel = getGradleSettingsModel();
    assertNotNull(settingsModel);
    assertEquals("include", ImmutableList.of(":app", ":lib", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.replaceModulePath("lib", "lib1");
    assertEquals("include", ImmutableList.of(":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        settingsModel.applyChanges();
      }
    });
    assertEquals("include", ImmutableList.of(":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());

    settingsModel.reparse();
    assertEquals("include", ImmutableList.of(":app", ":lib1", ":lib:subLib"), settingsModel.modulePaths());
  }
}
