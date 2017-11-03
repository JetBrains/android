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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link ContentRootsModuleSetupStep}.
 */
public class ContentRootsModuleSetupStepUnitTest {
  private ContentRootsModuleSetupStep mySetupStep;

  @Before
  public void setUp() {
    mySetupStep = new ContentRootsModuleSetupStep();
  }

  @Test
  public void invokeOnBuildVariantChange() {
    assertTrue(mySetupStep.invokeOnBuildVariantChange());
  }

  @Test
  public void invokeOnSkippedSync() {
    // See https://code.google.com/p/android/issues/detail?id=235647
    assertTrue(mySetupStep.invokeOnSkippedSync());
  }
}