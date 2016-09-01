/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.util.io.FileUtil;

import java.util.ArrayList;

import static com.android.tools.idea.npw.FormFactorUtils.ATTR_MODULE_NAME;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Tests for creating modules
 */
public class TemplateWizardModuleBuilderTest extends AndroidGradleTestCase {

  public void testConstructor() throws Exception {
    // Null project means creating a new project
    TemplateWizardModuleBuilder moduleBuilder1 =
      new TemplateWizardModuleBuilder(null, null, null, new ArrayList<>(), getTestRootDisposable(), false);
    assertTrue(moduleBuilder1.getWizardState().getBoolean(ATTR_IS_LAUNCHER));
    assertEquals(WizardUtils.getProjectLocationParent().getPath(), moduleBuilder1.getWizardState().get(ATTR_PROJECT_LOCATION));
    assertDoesntContain(moduleBuilder1.getWizardState().myHidden, ATTR_MODULE_NAME);

    assertNotNull(moduleBuilder1.getWizardState().get(ATTR_GRADLE_VERSION));
    assertNotNull(moduleBuilder1.getWizardState().get(ATTR_GRADLE_PLUGIN_VERSION));

    assertTrue(moduleBuilder1.getInitializationComplete());

    // Non-null project means we're adding a module to an existing project
    TemplateWizardModuleBuilder moduleBuilder2 =
      new TemplateWizardModuleBuilder(null, getProject(), null, new ArrayList<>(), getTestRootDisposable(), true);
    assertFalse(moduleBuilder2.getWizardState().getBoolean(ATTR_IS_LAUNCHER));
    assertContainsElements(moduleBuilder2.getWizardState().myHidden, ATTR_MODULE_NAME);

    assertNotNull(moduleBuilder2.getWizardState().get(ATTR_PROJECT_LOCATION));
    String basePath = getProject().getBasePath();
    assert basePath != null;
    assertEquals(FileUtil.toSystemIndependentName(basePath), moduleBuilder2.getWizardState().getString(ATTR_PROJECT_LOCATION));
  }
}
