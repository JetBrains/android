/*
 * Copyright (C) 2024 The Android Open Source Project
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