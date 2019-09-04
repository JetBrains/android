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
package com.android.tools.idea.gradle.util;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ProxySettingsTest {
  private final static String TEST_INPUT = "*,  ,,*.google.com|*|  | ||*.google.com| proxy.com , last.com";

  @Test
  public void replaceCommasWithPipes() {
    String expected = "*|*.google.com|*|*.google.com|proxy.com|last.com";
    assertThat(ProxySettings.replaceCommasWithPipesAndClean(TEST_INPUT)).isEqualTo(expected);
  }

  @Test
  public void replacePipesWithCommas() {
    String expected = "*, *.google.com, *, *.google.com, proxy.com, last.com";
    assertThat(ProxySettings.replacePipesWithCommasAndClean(TEST_INPUT)).isEqualTo(expected);
  }

  @Test
  public void replaceOnlySpaces() {
    assertThat(ProxySettings.replaceCommasWithPipesAndClean(" ,,  ,")).isNull();
    assertThat(ProxySettings.replacePipesWithCommasAndClean(" ,,  ,")).isNull();
  }

  @Test
  public void replaceNull() {
    assertThat(ProxySettings.replaceCommasWithPipesAndClean(null)).isNull();
    assertThat(ProxySettings.replacePipesWithCommasAndClean(null)).isNull();
  }
}
