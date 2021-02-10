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

import com.android.flags.Flag
import com.android.flags.FlagGroup
import com.android.flags.Flags
import com.android.flags.ImmutableFlagOverrides
import com.android.tools.idea.flags.overrides.ServerFlagOverrides
import com.android.tools.idea.serverflags.ServerFlagService
import com.google.common.truth.Truth
import org.junit.Test
import org.mockito.Mockito

private const val TEST_GROUP = "testgroup"
private const val STUDIO_FLAG_PREFIX = "studio_flags/$TEST_GROUP"

class ServerFlagOverridesTest {

  @Test
  fun testServerFlagOverrides() {
    val overrides: ImmutableFlagOverrides = ServerFlagOverrides()

    val flags = Flags(overrides)
    val group = FlagGroup(flags, TEST_GROUP, "display")
    val flagA = Flag.create(group, "a", "name_a", "description_a", false)
    val flagB = Flag.create(group, "b", "name_b", "description_b", true)
    val flagC = Flag.create(group, "c", "name_c", "description_c", false)

    Truth.assertThat(overrides.get(flagA)).isNull()
    Truth.assertThat(overrides.get(flagB)).isNull()
    Truth.assertThat(overrides.get(flagC)).isNull()

    val service = Mockito.mock(ServerFlagService::class.java)
    Mockito.`when`(service.initialized).thenReturn(true)
    Mockito.`when`(service.getBoolean("$STUDIO_FLAG_PREFIX.a")).thenReturn(true)
    Mockito.`when`(service.getBoolean("$STUDIO_FLAG_PREFIX.b")).thenReturn(false)
    Mockito.`when`(service.getBoolean("$STUDIO_FLAG_PREFIX.c")).thenReturn(null)
    ServerFlagService.instance = service

    Truth.assertThat(overrides.get(flagA)).isEqualTo("true")
    Truth.assertThat(overrides.get(flagB)).isEqualTo("false")
    Truth.assertThat(overrides.get(flagC)).isNull()
  }
}