/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link GradleExperimentalSettingsConfigurable}.
 */
public class GradleExperimentalSettingsConfigurableTest extends IdeaTestCase {
  @Mock private GradleExperimentalSettings mySettings;

  private GradleExperimentalSettingsConfigurable myConfigurable;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myConfigurable = new GradleExperimentalSettingsConfigurable(mySettings);
  }

  public void testIsModified() throws Exception {
    myConfigurable.setMaxModuleCountForSourceGen(6);
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 8;
    assertTrue(myConfigurable.isModified());
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 6;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setModuleSelectionOnImportEnabled(true);
    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = false;
    assertTrue(myConfigurable.isModified());
    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setSkipSourceGenOnSync(true);
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseNewProjectStructure(true);
    mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseNewGradleSync(true);
    mySettings.USE_NEW_GRADLE_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_NEW_GRADLE_SYNC = true;
    assertFalse(myConfigurable.isModified());

    myConfigurable.setUseL2DependenciesInSync(true);
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = false;
    assertTrue(myConfigurable.isModified());
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = true;
    assertFalse(myConfigurable.isModified());
  }

  public void testApply() throws ConfigurationException {
    myConfigurable.setMaxModuleCountForSourceGen(6);
    myConfigurable.setModuleSelectionOnImportEnabled(true);
    myConfigurable.setSkipSourceGenOnSync(true);
    myConfigurable.setUseNewProjectStructure(true);
    myConfigurable.setUseNewGradleSync(true);
    myConfigurable.setUseL2DependenciesInSync(true);

    myConfigurable.apply();

    assertEquals(6, mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
    assertTrue(mySettings.SELECT_MODULES_ON_PROJECT_IMPORT);
    assertTrue(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    assertTrue(mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG);
    assertTrue(mySettings.USE_NEW_GRADLE_SYNC);
    assertTrue(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);

    myConfigurable.setMaxModuleCountForSourceGen(8);
    myConfigurable.setModuleSelectionOnImportEnabled(false);
    myConfigurable.setSkipSourceGenOnSync(false);
    myConfigurable.setUseNewProjectStructure(false);
    myConfigurable.setUseNewGradleSync(false);
    myConfigurable.setUseL2DependenciesInSync(false);

    myConfigurable.apply();

    assertEquals(8, mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN);
    assertFalse(mySettings.SELECT_MODULES_ON_PROJECT_IMPORT);
    assertFalse(mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC);
    assertFalse(mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG);
    assertFalse(mySettings.USE_NEW_GRADLE_SYNC);
    assertFalse(mySettings.USE_L2_DEPENDENCIES_ON_SYNC);
  }

  public void testReset() {
    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = true;
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = true;
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 6;
    mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG = true;
    mySettings.USE_NEW_GRADLE_SYNC = true;
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = true;

    myConfigurable.reset();

    assertTrue(myConfigurable.isModuleSelectionOnImportEnabled());
    assertTrue(myConfigurable.isSkipSourceGenOnSync());
    assertEquals(6, myConfigurable.getMaxModuleCountForSourceGen().intValue());
    assertTrue(myConfigurable.isUseNewProjectStructureDialog());
    assertTrue(myConfigurable.isUseNewGradleSync());
    assertTrue(myConfigurable.isUseL2DependenciesInSync());

    mySettings.SELECT_MODULES_ON_PROJECT_IMPORT = false;
    mySettings.SKIP_SOURCE_GEN_ON_PROJECT_SYNC = false;
    mySettings.MAX_MODULE_COUNT_FOR_SOURCE_GEN = 8;
    mySettings.USE_NEW_PROJECT_STRUCTURE_DIALOG = false;
    mySettings.USE_NEW_GRADLE_SYNC = false;
    mySettings.USE_L2_DEPENDENCIES_ON_SYNC = false;

    myConfigurable.reset();

    assertFalse(myConfigurable.isModuleSelectionOnImportEnabled());
    assertFalse(myConfigurable.isSkipSourceGenOnSync());
    assertEquals(8, myConfigurable.getMaxModuleCountForSourceGen().intValue());
    assertFalse(myConfigurable.isUseNewProjectStructureDialog());
    assertFalse(myConfigurable.isUseNewGradleSync());
    assertFalse(myConfigurable.isUseL2DependenciesInSync());
  }
}