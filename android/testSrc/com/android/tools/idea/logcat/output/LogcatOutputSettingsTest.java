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
package com.android.tools.idea.logcat.output;

import com.intellij.testFramework.PlatformTestCase;

import static com.google.common.truth.Truth.assertThat;

public class LogcatOutputSettingsTest extends PlatformTestCase {

  @Override
  protected void tearDown() throws Exception {
    try {
      LogcatOutputSettings.getInstance().reset();
    } finally {
      super.tearDown();
    }
  }

  public void testDefaultValues() throws Exception {
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();
  }

  public void testSetters() throws Exception {
    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isTrue();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();

    LogcatOutputSettings.getInstance().setRunOutputEnabled(false);

    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isTrue();

    LogcatOutputSettings.getInstance().setDebugOutputEnabled(false);

    assertThat(LogcatOutputSettings.getInstance().isRunOutputEnabled()).isFalse();
    assertThat(LogcatOutputSettings.getInstance().isDebugOutputEnabled()).isFalse();
  }
}
