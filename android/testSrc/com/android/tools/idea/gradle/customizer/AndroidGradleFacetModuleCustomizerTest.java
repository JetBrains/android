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

import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.intellij.testFramework.IdeaTestCase;

/**
 * Tests for {@link AndroidGradleFacetModuleCustomizer}.
 */
public class AndroidGradleFacetModuleCustomizerTest extends IdeaTestCase {
  private AndroidGradleFacetModuleCustomizer myCustomizer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCustomizer = new AndroidGradleFacetModuleCustomizer();
  }

  public void testCustomizeModule() {
    myCustomizer.customizeModule(myModule, myProject, null);

    // Verify that AndroidGradleFacet was added.
    AndroidGradleFacet facet = Facets.getFirstFacet(myModule, AndroidGradleFacet.TYPE_ID);
    assertNotNull(facet);
  }
}
