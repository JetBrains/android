/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android.refactoring;

import org.junit.Test;

import static org.junit.Assert.*;

public class MigrateToAppCompatUsageInfoTest {
  @Test
  public void testVersionCompare() {
    assertEquals("1.0.0-alpha1",
                 MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion((String)null, "1.0.0-alpha1"));
    assertNull(MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion("$var", "1.0.0-alpha1"));
    assertEquals("1.0.0-alpha1",
                 MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion("", "1.0.0-alpha1"));
    assertEquals("1.0.0-alpha1",
                 MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion("1.0.0-alpha1", "1.0.0-alpha1"));
    assertEquals("1.0.0-alpha2",
                 MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion("1.0.0-alpha2", "1.0.0-alpha1"));
    assertEquals("1.0.0-alpha2",
                 MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion("1.0.0-alpha1", "1.0.0-alpha2"));
    assertEquals("1.0.1-alpha1",
                 MigrateToAppCompatUsageInfo.GradleDependencyUsageInfo.getHighestVersion("1.0.1-alpha1", "1.0.0-alpha2"));
  }

}