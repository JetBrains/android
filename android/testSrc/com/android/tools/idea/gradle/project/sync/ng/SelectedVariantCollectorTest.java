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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SelectedVariantCollector.SelectedVariant;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.JavaProjectTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.project.sync.Modules.createUniqueModuleId;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SelectedVariantCollector}.
 */
public class SelectedVariantCollectorTest extends JavaProjectTestCase {
  private SelectedVariantCollector myCollector;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCollector = new SelectedVariantCollector(getProject());
  }

  public void testAddSelectedVariantWithAndroidModule() {
    Module module = getModule();

    GradleModuleModel gradleModel = mock(GradleModuleModel.class);
    File rootPath = getBaseDirPath(getProject());
    when(gradleModel.getRootFolderPath()).thenReturn(rootPath);
    when(gradleModel.getGradlePath()).thenReturn(":app");

    GradleFacet facet = createAndAddGradleFacet(module);
    facet.setGradleModuleModel(gradleModel);

    IdeVariant variant = mock(IdeVariant.class);
    when(variant.getName()).thenReturn("release");
    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    when(androidModel.getSelectedVariant()).thenReturn(variant);

    AndroidFacet androidFacet = createAndAddAndroidFacet(module);
    androidFacet.getConfiguration().setModel(androidModel);
    androidFacet.getProperties().SELECTED_BUILD_VARIANT = "debug";

    SelectedVariant selectedVariant = myCollector.findSelectedVariant(module);
    assertNotNull(selectedVariant);

    assertEquals(createUniqueModuleId(rootPath, ":app"), selectedVariant.moduleId);
    // Verify that the selected variant is the same with the value in AndroidFacet, not AndroidModuleModel.
    assertEquals("debug", selectedVariant.variantName);
  }

  public void testAddSelectedVariantWithNonGradleModule() {
    SelectedVariant selectedVariant = myCollector.findSelectedVariant(getModule());
    assertNull(selectedVariant);
  }

  public void testAddSelectedVariantWithNonAndroidModule() {
    Module module = getModule();

    GradleFacet facet = createAndAddGradleFacet(module);
    facet.getConfiguration().GRADLE_PROJECT_PATH = ":app";

    SelectedVariant selectedVariant = myCollector.findSelectedVariant(getModule());
    assertNull(selectedVariant);
  }
}