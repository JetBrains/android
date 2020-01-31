/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import static com.android.tools.idea.run.tasks.ShellCommandLauncher.errorPattern;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ShellCommandLauncherTest {

  @Test
  public void testApplicationNameHasError() {
    String line = "Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] " +
                  "cmp=com.example.myapplicationerrortest/.MainActivity }";
    assertFalse(errorPattern.matcher(line).find());
  }

  @Test
  public void testErrorOutput() {
    String line = "Error: Activity not started, unable to ...";
    assertTrue(errorPattern.matcher(line).find());
  }

  @Test
  public void testErrorCodeOutput() {
    String line = "Error type 3";
    assertTrue(errorPattern.matcher(line).find());
  }
}