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
package com.android.tools.idea.adb

import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Test

/**
 * Tests synchronization of settings between the config panel and AdbOptionsService.
 */
class AdbConfigurableUiTest : LightPlatform4TestCase() {
  private lateinit var myConfigurable: AdbConfigurableUi
  private lateinit var myAdbOptionsService: AdbOptionsService
  private lateinit var myOriginalOptions: AdbOptionsService.AdbOptionsUpdater

  override fun setUp() {
    super.setUp()
    myAdbOptionsService = AdbOptionsService.getInstance()
    myOriginalOptions = myAdbOptionsService.getOptionsUpdater()
    myConfigurable = AdbConfigurableUi()
  }

  override fun tearDown() {
    // Restore the AdbOptionsService singleton to its original state so it doesn't break other tests
    myOriginalOptions.commit()
    super.tearDown()  // clears all fields of this class (!) so do it last
  }

  @Test
  fun testApply() {
    myAdbOptionsService.getOptionsUpdater().setUseLibusb(false).setUseMdnsOpenScreen(false).commit();
    myConfigurable.reset(myAdbOptionsService)

    myConfigurable.setLibusbEnabled(true)
    myConfigurable.setAdbMdnsEnabled(true)
    myConfigurable.apply(myAdbOptionsService)

    assertThat(myAdbOptionsService.shouldUseLibusb()).isTrue()
    assertThat(myAdbOptionsService.shouldUseMdnsOpenScreen()).isTrue()
  }

  @Test
  fun testReset() {
    myAdbOptionsService.getOptionsUpdater().setUseLibusb(true).setUseMdnsOpenScreen(true).commit();
    myConfigurable.reset(myAdbOptionsService)

    myConfigurable.setLibusbEnabled(false)
    myConfigurable.setAdbMdnsEnabled(false)
    myConfigurable.reset(myAdbOptionsService)

    assertThat(myConfigurable.isLibusbEnabled()).isTrue()
    assertThat(myConfigurable.isAdbMdnsEnabled()).isTrue()
  }

  @Test
  fun testIsModified() {
    myAdbOptionsService.getOptionsUpdater().setUseLibusb(false).setUseMdnsOpenScreen(false).commit();
    myConfigurable.reset(myAdbOptionsService)
    assertThat(myConfigurable.isModified(myAdbOptionsService)).isFalse()

    myConfigurable.setAdbMdnsEnabled(true)
    assertThat(myConfigurable.isModified(myAdbOptionsService)).isTrue()

    myConfigurable.reset(myAdbOptionsService)
    myConfigurable.setLibusbEnabled(true)
    assertThat(myConfigurable.isModified(myAdbOptionsService)).isTrue()
  }
}
