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
import org.junit.Assert
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
    myConfigurable = AdbConfigurableUi().also {
      // Force lazy-init of JComboBoxes
      it.component
    }
  }

  override fun tearDown() {
    // Restore the AdbOptionsService singleton to its original state so it doesn't break other tests
    myOriginalOptions.commit()
    super.tearDown()  // clears all fields of this class (!) so do it last
  }

  @Test
  fun testApply() {
    myAdbOptionsService.getOptionsUpdater().setAdbServerUsbBackend(AdbServerUsbBackend.DEFAULT).setAdbServerMdnsBackend(AdbServerMdnsBackend.DEFAULT).commit();
    myConfigurable.reset(myAdbOptionsService)

    myConfigurable.setAdbServerUsbBackend(AdbServerUsbBackend.LIBUSB)
    myConfigurable.setAdbServerMdnsBackend(AdbServerMdnsBackend.OPENSCREEN)
    myConfigurable.apply(myAdbOptionsService)

    assertThat(myAdbOptionsService.adbServerUsbBackend).isEqualTo(AdbServerUsbBackend.LIBUSB)
    assertThat(myAdbOptionsService.adbServerMdnsBackend).isEqualTo(AdbServerMdnsBackend.OPENSCREEN)
  }

  @Test
  fun testReset() {
    myAdbOptionsService.getOptionsUpdater().setAdbServerUsbBackend(AdbServerUsbBackend.DEFAULT).setAdbServerMdnsBackend(AdbServerMdnsBackend.DEFAULT).commit();
    myConfigurable.reset(myAdbOptionsService)

    myConfigurable.setAdbServerUsbBackend(AdbServerUsbBackend.LIBUSB)
    myConfigurable.setAdbServerMdnsBackend(AdbServerMdnsBackend.OPENSCREEN)
    myConfigurable.reset(myAdbOptionsService)

    assertThat(myConfigurable.adbServerUsbBackend).isEqualTo(AdbServerUsbBackend.DEFAULT)
    assertThat(myConfigurable.adbServerMdnsBackend).isEqualTo(AdbServerMdnsBackend.DEFAULT)
  }

  @Test
  fun testIsModified() {
    myAdbOptionsService.getOptionsUpdater().setAdbServerUsbBackend(AdbServerUsbBackend.DEFAULT).setAdbServerMdnsBackend(AdbServerMdnsBackend.DEFAULT).commit();
    myConfigurable.reset(myAdbOptionsService)
    assertThat(myConfigurable.isModified(myAdbOptionsService)).isFalse()

    myConfigurable.setAdbServerMdnsBackend(AdbServerMdnsBackend.OPENSCREEN)
    assertThat(myConfigurable.isModified(myAdbOptionsService)).isTrue()

    myConfigurable.reset(myAdbOptionsService)
    myConfigurable.setAdbServerUsbBackend(AdbServerUsbBackend.LIBUSB)
    assertThat(myConfigurable.isModified(myAdbOptionsService)).isTrue()
  }

  @Test
  fun testFromDisplayText() {
    AdbServerUsbBackend.values().forEach {
      assertThat(AdbServerUsbBackend.fromDisplayText(it.displayText)).isEqualTo(it)
    }
    try {
       AdbServerUsbBackend.fromDisplayText("NotInTheEnum")
      Assert.fail("fromDisplayText did no throw")
    } catch (_: IllegalArgumentException) {
      // Expected
    }
  }
}
