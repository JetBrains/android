/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.AndroidTestCase;

import static org.jetbrains.android.facet.AndroidFacet.ID;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AndroidFacetTest extends AndroidTestCase {

  private Module myDisposedModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDisposedModule = mock(Module.class);
    when(myDisposedModule.isDisposed()).thenReturn(true);
  }

  public void testDisposedModule() {
    assertNull(AndroidFacet.getInstance(myDisposedModule));
  }

  public void testWithEmptyModelsProvider() {
    IdeModifiableModelsProvider modelsProvider = mock(IdeModifiableModelsProvider.class);
    ModifiableFacetModel facetModel = mock(ModifiableFacetModel.class);
    Module cachedModule = mock(Module.class);
    FacetManager cachedFacetManger = mock(FacetManager.class);
    AndroidFacet cachedFacet = mock(AndroidFacet.class);

    // Simulate the case when there are no facets in modelsProvider, but there are cached facets in
    // module's FacetManager component
    when(modelsProvider.getModifiableFacetModel(cachedModule)).thenReturn(facetModel);
    when(facetModel.getAllFacets()).thenReturn(Facet.EMPTY_ARRAY);

    when(cachedModule.getComponent(FacetManager.class)).thenReturn(cachedFacetManger);
    when(cachedFacetManger.getFacetByType(ID)).thenReturn(cachedFacet);

    assertNull(AndroidFacet.getInstance(cachedModule, modelsProvider));
  }
}
