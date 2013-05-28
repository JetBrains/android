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
package com.android.tools.idea.gradle.variant.view;

import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link BuildVariantView}.
 */
public class BuildVariantViewTest extends AndroidTestCase {
  private boolean myExpectedListenerCalled;
  private Listener myListener;
  private BuildVariantUpdater myUpdater;
  private BuildVariantView myView;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myUpdater = createMock(BuildVariantUpdater.class);
    myView = BuildVariantView.getInstance(getProject());
    myView.setUpdater(myUpdater);
    myListener = new Listener();
    myView.addListener(myListener);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    // After tearDown, the buildVariantSelected method is called (if expected to be called.)
    assertEquals(myExpectedListenerCalled, myListener.myWasCalled);
  }

  public void testBuildVariantSelectedWithSuccessfulUpdate() {
    myExpectedListenerCalled = true;

    String buildVariantName = "debug";
    expect(myUpdater.updateModule(getProject(), myModule.getName(), buildVariantName)).andReturn(myFacet);
    replay(myUpdater);

    myView.buildVariantSelected(myModule.getName(), buildVariantName);

    verify(myUpdater);
  }

  public void testBuildVariantSelectedWithFailedUpdate() {
    myExpectedListenerCalled = false;

    String buildVariantName = "debug";
    expect(myUpdater.updateModule(getProject(), myModule.getName(), buildVariantName)).andReturn(null);
    replay(myUpdater);

    myView.buildVariantSelected(myModule.getName(), buildVariantName);

    verify(myUpdater);
  }

  private static class Listener implements BuildVariantView.BuildVariantSelectionChangeListener {
    AndroidFacet myUpdatedFacet;
    boolean myWasCalled;

    @Override
    public void buildVariantSelected(@NotNull AndroidFacet updatedFacet) {
      myUpdatedFacet = updatedFacet;
      myWasCalled = true;
    }
  }
}
