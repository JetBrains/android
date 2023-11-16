// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.actions;

import static org.mockito.Mockito.when;

import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.testing.Facets;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mockito;

public class AndroidToolsActionGroupTest extends LightPlatformTestCase {

  private AndroidToolsActionGroup androidGroup;
  private IdeSdks mockSdkService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    assert !ProjectFacetManager.getInstance(getProject()).hasFacets(AndroidFacet.ID) :
      "Android facet should not be added permanently to light project";
    androidGroup = new AndroidToolsActionGroup();

    mockSdkService = Mockito.mock(IdeSdks.class);
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), IdeSdks.class, mockSdkService, getTestRootDisposable());
  }

  public void testShouldBeVisibleWhenSdkConfigured() {
    when(mockSdkService.hasConfiguredAndroidSdk()).thenReturn(true);
    assertTrue(androidGroup.shouldBeVisible(getProject()));
  }

  public void testShouldNotBeVisibleWhenNoSdkConfiguredAndNoAndroidFacet() {
    when(mockSdkService.hasConfiguredAndroidSdk()).thenReturn(false);
    assertFalse(androidGroup.shouldBeVisible(getProject()));
  }

  public void testShouldBeVisibleWhenNoSdkConfiguredAndAndroidFacetExists() {
    when(mockSdkService.hasConfiguredAndroidSdk()).thenReturn(false);
    Facets.withAndroidFacet(getModule(), () -> {
      assertTrue(androidGroup.shouldBeVisible(getProject()));
    });
  }
}