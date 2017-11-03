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
package com.android.tools.idea.testing;

import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import static org.mockito.Mockito.mock;

/**
 * Tests for {@link IdeComponents}.
 */
public class IdeComponentsTest extends IdeaTestCase {
  public void testReplaceApplicationService() {
    SdkSync originalSdkSync = SdkSync.getInstance();
    IdeComponents ideComponents = new IdeComponents(myProject);
    try {
      SdkSync mockSdkSync = mock(SdkSync.class);
      ideComponents.replaceService(SdkSync.class, mockSdkSync);
      assertSame(mockSdkSync, SdkSync.getInstance());
    }
    finally {
      ideComponents.restore();
      assertSame(originalSdkSync, SdkSync.getInstance());
    }
  }

  public void testReplaceProjectService() {
    GradleSettings mockSettings = mock(GradleSettings.class);
    IdeComponents.replaceService(getProject(), GradleSettings.class, mockSettings);
    assertSame(mockSettings, GradleSettings.getInstance(getProject()));
  }
}