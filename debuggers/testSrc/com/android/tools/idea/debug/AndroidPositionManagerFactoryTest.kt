/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.debug.AndroidPositionManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule.Companion.inMemory
import com.google.common.truth.Truth
import com.intellij.debugger.engine.DebugProcessImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit

class AndroidPositionManagerFactoryTest {
  @Rule
  val myMockitoRule = MockitoJUnit.rule()

  @Rule
  val myRestoreFlagRule: RestoreFlagRule<*> = RestoreFlagRule<Any?>(StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE)

  @Rule
  val myAndroidProjectRule = inMemory()

  @Mock
  private val mockDebugProcessImpl: DebugProcessImpl? = null
  private val myFactory = AndroidPositionManagerFactory()
  @Before
  fun setup() {
    Mockito.`when`(mockDebugProcessImpl!!.project).thenReturn(myAndroidProjectRule.project)
  }

  @Test
  fun createPositionManager_debugDeviceSdkSourcesEnabled() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(true)
    Truth.assertThat(myFactory.createPositionManager(mockDebugProcessImpl!!)).isInstanceOf(AndroidPositionManager::class.java)
  }

  @Test
  fun createPositionManager_debugDeviceSdkSourcesNotEnabled() {
    StudioFlags.DEBUG_DEVICE_SDK_SOURCES_ENABLE.override(false)
    Truth.assertThat(myFactory.createPositionManager(mockDebugProcessImpl!!)).isInstanceOf(AndroidPositionManagerOriginal::class.java)
  }
}