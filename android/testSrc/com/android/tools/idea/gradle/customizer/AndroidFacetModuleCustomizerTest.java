/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.TestProjects;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.android.tools.idea.gradle.util.Facets;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;

/**
 * Tests for {@link AndroidFacetModuleCustomizer}.
 */
public class AndroidFacetModuleCustomizerTest extends IdeaTestCase {
  private AndroidProjectStub myAndroidProject;
  private AndroidFacetModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    File rootDirPath = new File(myProject.getBasePath());
    myAndroidProject = TestProjects.createBasicProject(rootDirPath);
    myAndroidProject.setIsLibrary(true);
    myCustomizer = new AndroidFacetModuleCustomizer();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  public void testCustomizeModule() {
    String rootDirPath = myAndroidProject.getRootDir().getAbsolutePath();
    VariantStub selectedVariant = myAndroidProject.getFirstVariant();
    assertNotNull(selectedVariant);
    String selectedVariantName = selectedVariant.getName();
    IdeaAndroidProject project = new IdeaAndroidProject(myAndroidProject.getName(), rootDirPath, myAndroidProject, selectedVariantName);
    myCustomizer.customizeModule(myModule, myProject, project);

    // Verify that AndroidFacet was added and configured.
    AndroidFacet facet = Facets.getFirstFacetOfType(myModule, AndroidFacet.ID);
    assertNotNull(facet);
    assertSame(project, facet.getIdeaAndroidProject());

    JpsAndroidModuleProperties facetState = facet.getConfiguration().getState();
    assertFalse(facetState.ALLOW_USER_CONFIGURATION);
  }
}
