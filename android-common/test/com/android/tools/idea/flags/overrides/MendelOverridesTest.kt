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
package com.android.tools.idea.flags.overrides

import com.android.flags.BooleanFlag
import com.android.flags.FlagGroup
import com.android.flags.Flags
import com.android.flags.ImmutableFlagOverrides
import com.android.flags.MendelFlag
import com.android.tools.idea.mendel.MendelFlagsProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.registerExtension
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

private const val TEST_GROUP = "testgroup"

class MendelOverridesTest {
  @get:Rule
  val appRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private val service = mock(MendelFlagsProvider::class.java)

  @Before
  fun setUp() {
    ApplicationManager.getApplication().registerExtension(
      MendelFlagsProvider.EP_NAME, service, disposableRule.disposable
    )
  }

  @Test
  fun testMendelOverrides() {
    val overrides: ImmutableFlagOverrides = MendelOverrides()
    val flags = Flags(overrides)
    val group = FlagGroup(flags, TEST_GROUP, "display")
    val flagA = MendelFlag(group, "a", 1, "name_a", "description_a", false)
    val flagB = MendelFlag(group, "b", 2, "name_b", "description_b", true)
    val flagC = BooleanFlag(group, "c", "name_c", "description_c", true)
    assertThat(overrides.get(flagA)).isEqualTo("false")
    assertThat(overrides.get(flagB)).isEqualTo("false")
    assertThat(overrides.get(flagC)).isNull()

    whenever(service.getActiveExperimentIds()).thenReturn(listOf(1))

    assertThat(overrides.get(flagA)).isEqualTo("true")
    assertThat(overrides.get(flagB)).isEqualTo("false")
    assertThat(overrides.get(flagC)).isNull()
  }
}