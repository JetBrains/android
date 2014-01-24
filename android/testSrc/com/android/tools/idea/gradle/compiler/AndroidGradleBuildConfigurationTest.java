/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.compiler;

import com.google.common.collect.Lists;
import junit.framework.TestCase;

import java.util.Collection;

/**
 * Tests for {@link AndroidGradleBuildConfiguration}.
 */
public class AndroidGradleBuildConfigurationTest extends TestCase {
  private AndroidGradleBuildConfiguration myConfiguration;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myConfiguration = new AndroidGradleBuildConfiguration();
  }

  public void testGetCommandLineOptions() {
    myConfiguration.COMMAND_LINE_OPTIONS = "--stacktrace   --offline  --debug --all";
    Collection<String> options = Lists.newArrayList(myConfiguration.getCommandLineOptions());
    assertEquals(4, options.size());
    assertTrue(options.contains("--stacktrace"));
    assertTrue(options.contains("--offline"));
    assertTrue(options.contains("--debug"));
    assertTrue(options.contains("--all"));
  }
}
