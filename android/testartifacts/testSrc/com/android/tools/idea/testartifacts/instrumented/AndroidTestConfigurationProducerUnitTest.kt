/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented

import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.diagnostic.Logger
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Unit test for [AndroidTestConfigurationProducer]
 */
class AndroidTestConfigurationProducerUnitTest {

  @Test
  fun extraOptionsAreAddedByExtension() {
    val extension = mock<TestRunConfigurationOptions>()
    `when`(extension.getExtraOptions(any())).thenReturn(listOf("-e key_only", "-e key3 value3"))
      .thenThrow(IllegalStateException())

    val options = AndroidTestConfigurationProducer.getOptions(
      existingOptions = "-e key1 value1",
      mock(),
      listOf(extension),
      mock()
    )

    assertThat(options).isEqualTo("-e key1 value1 -e key_only  -e key3 value3")
  }

  @Test
  fun getOptionsShouldFallbackToEmptyString() {
    val mockLogger = mock<Logger>()

    val extensionThatThrowsException = mock<TestRunConfigurationOptions>()
    `when`(extensionThatThrowsException.getExtraOptions(any()))
      .thenThrow(IllegalStateException())

    val options = AndroidTestConfigurationProducer.getOptions(
      existingOptions = "-e key1 value1",
      mock(),
      listOf(extensionThatThrowsException),
      mockLogger,
    )

    verify(mockLogger).error(any(), any(IllegalStateException::class.java))
    assertThat(options).isEqualTo("-e key1 value1")
  }
}
