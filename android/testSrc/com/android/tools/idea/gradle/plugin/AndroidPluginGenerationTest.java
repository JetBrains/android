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
package com.android.tools.idea.gradle.plugin;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link AndroidPluginGeneration}.
 */
public class AndroidPluginGenerationTest {
  @Test
  public void findWithOriginalArtifactIdAndGroupId() {
    AndroidPluginGeneration generation = AndroidPluginGeneration.find("gradle", "com.android.tools.build");
    assertSame(AndroidPluginGeneration.ORIGINAL, generation);
  }

  @Test
  public void findWithWRONGArtifactIdAndGroupId() {
    AndroidPluginGeneration generation = AndroidPluginGeneration.find("HELLO", "WORLD");
    assertNull(generation);
  }
}