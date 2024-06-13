/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.flags.IntFlag
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.serverflags.ServerFlagService
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.unregisterService
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

private const val TEST_GROUP = "testgroup"
private const val STUDIO_FLAG_PREFIX = "studio_flags/$TEST_GROUP"

class ServerFlagOverridesTest {
  @get:Rule
  val appRule = ApplicationRule()
  @get:Rule
  val disposableRule = DisposableRule()

  @After
  fun tearDown() {
    ApplicationManager.getApplication().unregisterService(ServerFlagService::class.java)
  }

  @Test
  fun testServerFlagOverrides() {
    val overrides: ImmutableFlagOverrides = ServerFlagOverrides()
    val flags = Flags(overrides)
    val group = FlagGroup(flags, TEST_GROUP, "display")
    val flagA = BooleanFlag(group, "a", "name_a", "description_a", false)
    val flagB = BooleanFlag(group, "b", "name_b", "description_b", true)
    val flagC = BooleanFlag(group, "c", "name_c", "description_c", false)

    assertThat(overrides.get(flagA)).isNull()
    assertThat(overrides.get(flagB)).isNull()
    assertThat(overrides.get(flagC)).isNull()

    val service = Mockito.mock(ServerFlagService::class.java)
    whenever(service.getBoolean("$STUDIO_FLAG_PREFIX.a")).thenReturn(true)
    whenever(service.getBoolean("$STUDIO_FLAG_PREFIX.b")).thenReturn(false)
    whenever(service.getBoolean("$STUDIO_FLAG_PREFIX.c")).thenReturn(null)
    ApplicationManager.getApplication().registerServiceInstance(ServerFlagService::class.java, service)

    assertThat(overrides.get(flagA)).isEqualTo("true")
    assertThat(overrides.get(flagB)).isEqualTo("false")
    assertThat(overrides.get(flagC)).isNull()
  }

  @Test
  fun testIntServerFlagOverrides() {
    val overrides: ImmutableFlagOverrides = ServerFlagOverrides()
    val flags = Flags(overrides)
    val group = FlagGroup(flags, TEST_GROUP, "display")
    val flagD = IntFlag(group, "d", "name_d", "description_d", 0)
    val flagE = IntFlag(group, "e", "name_e", "description_e", 1)
    val flagF = IntFlag(group, "f", "name_f", "description_f", 0)

    assertThat(overrides.get(flagD)).isNull()
    assertThat(overrides.get(flagE)).isNull()
    assertThat(overrides.get(flagF)).isNull()

    val service = Mockito.mock(ServerFlagService::class.java)
    whenever(service.getInt("$STUDIO_FLAG_PREFIX.d")).thenReturn(1)
    whenever(service.getInt("$STUDIO_FLAG_PREFIX.e")).thenReturn(0)
    whenever(service.getInt("$STUDIO_FLAG_PREFIX.f")).thenReturn(null)
    ApplicationManager.getApplication().registerServiceInstance(ServerFlagService::class.java, service)

    assertThat(overrides.get(flagD)).isEqualTo("1")
    assertThat(overrides.get(flagE)).isEqualTo("0")
    assertThat(overrides.get(flagF)).isNull()
  }
}