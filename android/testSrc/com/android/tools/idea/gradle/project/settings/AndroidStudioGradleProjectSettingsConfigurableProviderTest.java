/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.settings;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidStudioGradleProjectSettingsConfigurableProvider}.
 */
public class AndroidStudioGradleProjectSettingsConfigurableProviderTest extends IdeaTestCase {
  @Mock private IdeInfo myIdeInfo;

  private IdeComponents myIdeComponents;
  private AndroidStudioGradleProjectSettingsConfigurableProvider myProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myIdeComponents = new IdeComponents(project);
    myIdeComponents.replaceService(IdeInfo.class, myIdeInfo);

    myProvider = new AndroidStudioGradleProjectSettingsConfigurableProvider(project);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
      myProvider = null;
    }
  }

  public void testCreateConfigurableWithAndroidStudio() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);
    Configurable configurable = myProvider.createConfigurable();
    assertThat(configurable).isInstanceOf(AndroidStudioGradleProjectSettingsConfigurable.class);
  }

  public void testCreateConfigurableWithIdeNotAndroidStudio() {
    when(myIdeInfo.isAndroidStudio()).thenReturn(false);
    Configurable configurable = myProvider.createConfigurable();
    assertNull(configurable);
  }
}