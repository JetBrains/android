/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade;

import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater.UpdateResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link UpdateResult}.
 */
public class UpdateResultTest {
  private UpdateResult myResult;

  @Before
  public void setUp() {
    myResult = new UpdateResult();
  }

  @Test
  public void versionUpdateSuccessfulWithPluginVersionUpdate() {
    myResult.pluginVersionUpdated();
    assertTrue(myResult.versionUpdateSuccess());
  }

  @Test
  public void versionUpdateSuccessfulWithGradleVersionUpdate() {
    myResult.gradleVersionUpdated();
    assertTrue(myResult.versionUpdateSuccess());
  }

  @Test
  public void versionUpdateSuccessfulWithPluginVersionUpdateError() {
    myResult.setPluginVersionUpdateError(new Throwable());
    assertFalse(myResult.versionUpdateSuccess());
  }

  @Test
  public void versionUpdateSuccessfulWithGradleVersionUpdateError() {
    myResult.setGradleVersionUpdateError(new Throwable());
    assertFalse(myResult.versionUpdateSuccess());
  }

  @Test
  public void versionUpdateSuccessfulWithPluginVersionUpdateAndGradleVersionUpdateError() {
    myResult.pluginVersionUpdated();
    myResult.setGradleVersionUpdateError(new Throwable());
    assertFalse(myResult.versionUpdateSuccess());
  }

  @Test
  public void versionUpdateSuccessfulWithGradleVersionUpdateAndPluginVersionUpdateError() {
    myResult.gradleVersionUpdated();
    myResult.setPluginVersionUpdateError(new Throwable());
    assertFalse(myResult.versionUpdateSuccess());
  }
}