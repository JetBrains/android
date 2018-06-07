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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.testFramework.IdeaTestCase;

/**
 * Tests for {@link com.android.tools.idea.flags.StudioFlags} that are specific to the Gradle Project System.
 */
public class GradleProjectSystemFlagsTest extends IdeaTestCase {
  public void testNewSyncEnabledTest() {
    assertFalse(StudioFlags.NEW_SYNC_INFRA_ENABLED.get());
  }

  public void testNewPsdEnabledTest() {
    assertFalse(StudioFlags.NEW_PSD_ENABLED.get());
  }
}
