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
package com.android.tools.idea.databinding;

import com.android.tools.idea.testing.Facets;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * Tests for {@link ModuleDataBinding}.
 */
public class ModuleDataBindingTest extends IdeaTestCase {
  private AndroidFacet myFacet;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFacet = Facets.createAndAddAndroidFacet(getModule());
  }

  public void testIsAndSetEnabled() {
    assertNull(ModuleDataBinding.getInstance(myFacet));
    assertFalse(ModuleDataBinding.isEnabled(myFacet));

    // Enable data binding.
    ModuleDataBinding.enable(myFacet);
    ModuleDataBinding dataBinding = ModuleDataBinding.getInstance(myFacet);
    assertNotNull(dataBinding);
    assertTrue(ModuleDataBinding.isEnabled(myFacet));

    // Try to enable data binding again.
    ModuleDataBinding.enable(myFacet);
    // Should not create another ModuleDataBinding.
    assertSame(dataBinding, ModuleDataBinding.getInstance(myFacet));

    // Disable data binding.
    ModuleDataBinding.disable(myFacet);
    assertNull(ModuleDataBinding.getInstance(myFacet));
    assertFalse(ModuleDataBinding.isEnabled(myFacet));
  }
}