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

import com.android.builder.model.AndroidProject;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.PreciseRevision;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link BuildVariantView}.
 */
public class BuildVariantViewTest extends AndroidTestCase {
  private Listener myListener;
  private BuildVariantUpdater myUpdater;
  private BuildVariantView myView;
  private List<AndroidFacet> myAndroidFacets;
  private String myBuildVariantName;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myUpdater = createMock(BuildVariantUpdater.class);
    myView = BuildVariantView.getInstance(getProject());
    myView.setUpdater(myUpdater);
    myListener = new Listener();
    myView.addListener(myListener);
    myAndroidFacets = Collections.singletonList(myFacet);
    myBuildVariantName = "debug";
  }

  public void testSelectVariantWithSuccessfulUpdate() {
    expect(myUpdater.updateSelectedVariant(getProject(), myModule.getName(), myBuildVariantName)).andStubReturn(myAndroidFacets);
    replay(myUpdater);

    myView.buildVariantSelected(myModule.getName(), myBuildVariantName);
    assertTrue(myListener.myWasCalled);

    verify(myUpdater);
  }

  public void testSelectVariantWithFailedUpdate() {
    List<AndroidFacet> facets = Collections.emptyList();
    expect(myUpdater.updateSelectedVariant(getProject(), myModule.getName(), myBuildVariantName)).andStubReturn(facets);
    replay(myUpdater);

    myView.buildVariantSelected(myModule.getName(), myBuildVariantName);
    assertFalse(myListener.myWasCalled);

    verify(myUpdater);
  }

  public void testSupportsUnitTestWithUnsupportedVersion() {
    AndroidProject project = createMock(AndroidProject.class);
    expect(project.getModelVersion()).andStubReturn("1.0.1");
    replay(project);

    assertFalse(BuildVariantView.supportsUnitTests(project, getModelVersionSupportingUnitTests()));

    verify(project);
  }

  public void testSupportsUnitTestWithSupportedReleaseCandidateVersion() {
    AndroidProject project = createMock(AndroidProject.class);
    expect(project.getModelVersion()).andStubReturn("1.1.0-rc1");
    replay(project);

    assertTrue(BuildVariantView.supportsUnitTests(project, getModelVersionSupportingUnitTests()));

    verify(project);
  }

  public void testSupportsUnitTestWithSupportedVersion() {
    AndroidProject project = createMock(AndroidProject.class);
    expect(project.getModelVersion()).andStubReturn("1.1.0");
    replay(project);

    assertTrue(BuildVariantView.supportsUnitTests(project, getModelVersionSupportingUnitTests()));

    verify(project);
  }

  @NotNull
  private static FullRevision getModelVersionSupportingUnitTests() {
    return new PreciseRevision(1, 1, 0);
  }

  private static class Listener implements BuildVariantView.BuildVariantSelectionChangeListener {
    boolean myWasCalled;

    @Override
    public void buildVariantsConfigChanged() {
      myWasCalled = true;
    }
  }
}
