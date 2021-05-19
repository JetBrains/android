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
package com.android.tools.idea.testartifacts.instrumented.testsuite.api

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import java.time.Duration

/**
 * Unit tests for [AndroidTestResults].
 */
@RunWith(JUnit4::class)
class AndroidTestResultsTest {

  @Mock lateinit var mockDevice: AndroidDevice

  @Before
  fun setup() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun roundedDuration() {
    assertThat(createAndroidTestResults().getRoundedDuration(mockDevice)).isNull()
    assertThat(createAndroidTestResults(Duration.ofMillis(500)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(500))
    assertThat(createAndroidTestResults(Duration.ofMillis(999)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(999))
    assertThat(createAndroidTestResults(Duration.ofMillis(1000)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(1000))
    assertThat(createAndroidTestResults(Duration.ofMillis(1001)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(1000))
    assertThat(createAndroidTestResults(Duration.ofMillis(1500)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(1000))
    assertThat(createAndroidTestResults(Duration.ofMillis(2500)).getRoundedDuration(mockDevice)).isEqualTo(Duration.ofMillis(2000))
  }

  private fun createAndroidTestResults(duration: Duration? = null): AndroidTestResults {
    val results = mock<AndroidTestResults>()
    `when`(results.getDuration(any())).thenReturn(duration)
    return results
  }
}