/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import static org.junit.Assert.*;

import com.android.ide.common.repository.AgpVersion;
import org.junit.Test;

public class GradleProjectSystemUtilTest {
  @Test
  public void useCompatibilityConfigurationNames() {
    assertTrue(GradleProjectSystemUtil.useCompatibilityConfigurationNames(AgpVersion.parse("2.3.2")));
    assertFalse(GradleProjectSystemUtil.useCompatibilityConfigurationNames((AgpVersion)null));
    assertFalse(GradleProjectSystemUtil.useCompatibilityConfigurationNames(AgpVersion.parse("3.0.0-alpha1")));
    assertFalse(GradleProjectSystemUtil.useCompatibilityConfigurationNames(AgpVersion.parse("3.0.0")));
    assertFalse(GradleProjectSystemUtil.useCompatibilityConfigurationNames(AgpVersion.parse("4.0.0")));
  }
}