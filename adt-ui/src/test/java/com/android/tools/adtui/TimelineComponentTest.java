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
package com.android.tools.adtui;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.android.tools.adtui.TimelineComponent.formatTime;
import static com.google.common.truth.Truth.assertThat;

public class TimelineComponentTest {

  @Test
  public void testFormatTime() {
    assertThat(formatTime(0)).isEqualTo("0s");
    assertThat(formatTime(42)).isEqualTo("42s");
    assertThat(formatTime(59)).isEqualTo("59s");
    assertThat(formatTime(60)).isEqualTo("1m 0s");
    assertThat(formatTime(3599)).isEqualTo("59m 59s");
    assertThat(formatTime(3600)).isEqualTo("1h 0m 0s");
    assertThat(formatTime(86399)).isEqualTo("23h 59m 59s");
    assertThat(formatTime(86400)).isEqualTo("24h 0m 0s");
    assertThat(formatTime(1000000)).isEqualTo("277h 46m 40s");
  }
}
