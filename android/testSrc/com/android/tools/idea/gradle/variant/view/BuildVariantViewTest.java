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

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link BuildVariantView}.
 */
public class BuildVariantViewTest extends AndroidTestCase {
  private Listener myListener;
  private BuildVariantUpdater myUpdater;
  private BuildVariantView myView;
  private String myBuildVariantName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myUpdater = createMock(BuildVariantUpdater.class);
    myView = BuildVariantView.getInstance(getProject());
    myView.setUpdater(myUpdater);
    myListener = new Listener();
    myView.addListener(myListener);
    myBuildVariantName = "debug";
  }

  public void testSelectVariantWithSuccessfulUpdate() {
    expect(myUpdater.updateSelectedVariant(getProject(), myModule.getName(), myBuildVariantName)).andStubReturn(true);
    replay(myUpdater);

    myView.buildVariantSelected(myModule.getName(), myBuildVariantName);
    assertTrue(myListener.myWasCalled);

    verify(myUpdater);
  }

  public void testSelectVariantWithFailedUpdate() {
    expect(myUpdater.updateSelectedVariant(getProject(), myModule.getName(), myBuildVariantName)).andStubReturn(false);
    replay(myUpdater);

    myView.buildVariantSelected(myModule.getName(), myBuildVariantName);
    assertFalse(myListener.myWasCalled);

    verify(myUpdater);
  }

  private static class Listener implements BuildVariantView.BuildVariantSelectionChangeListener {
    boolean myWasCalled;

    @Override
    public void buildVariantsConfigChanged() {
      myWasCalled = true;
    }
  }
}
