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

import com.android.tools.idea.testing.Facets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link AndroidFacetScopedService}.
 */
public class AndroidFacetScopedServiceTest extends IdeaTestCase {
  private AndroidFacet myFacet;
  private MyAndroidFacetScopedService myService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Key<AndroidFacetScopedService> key = Key.create(MyAndroidFacetScopedService.class.getName());

    Module module = getModule();

    myFacet = Facets.createAndAddAndroidFacet(module);
    myService = new MyAndroidFacetScopedService(myFacet);
    module.putUserData(key, myService);
  }

  public void testGetFacet() {
    assertSame(myFacet, myService.getFacet());
  }

  public void testDispose() {
    Disposer.dispose(myFacet);

    assertTrue(myService.isDisposed());
    assertTrue(myService.onServiceDisposalInvoked);

    try {
      myService.getFacet();
      fail("Expecting IllegalStateException");
    }
    catch (IllegalStateException ignored) {
    }
  }

  private static class MyAndroidFacetScopedService extends AndroidFacetScopedService {
    boolean onServiceDisposalInvoked;

    MyAndroidFacetScopedService(@NotNull AndroidFacet facet) {
      super(facet);
    }

    @Override
    protected void onServiceDisposal(@NotNull AndroidFacet facet) {
      onServiceDisposalInvoked = true;
    }
  }
}