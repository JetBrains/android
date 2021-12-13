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
package com.android.tools.idea.wearpairing

import com.android.sdklib.AndroidVersion
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.idea.devicemanager.legacy.AvdUiAction.AvdInfoProvider
import com.android.tools.idea.devicemanager.legacy.PairDeviceAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito
import org.jetbrains.android.util.AndroidBundle.message

class PairDeviceActionTest {

  @Test
  fun enabledAllWearDevices() {
    PairDeviceAction(createAvdInfoProvider(SystemImage.WEAR_TAG, apiLevel = 29, hasPlayStore = false)).apply {
      assertThat(description).isEqualTo(message("wear.assistant.device.list.tooltip.ok"))
      assertThat(isEnabled).isTrue()
    }
  }

  @Test
  fun disabledLowApiPhones() {
    PairDeviceAction(createAvdInfoProvider(SystemImage.DEFAULT_TAG, apiLevel = 29, true)).apply {
      assertThat(description).isEqualTo(message("wear.assistant.device.list.tooltip.requires.api"))
      assertThat(isEnabled).isFalse()
    }
  }

  @Test
  fun disabledNonPlayStorePhones() {
    PairDeviceAction(createAvdInfoProvider(SystemImage.DEFAULT_TAG, apiLevel = 30, hasPlayStore = false)).apply {
      assertThat(description).isEqualTo(message("wear.assistant.device.list.tooltip.requires.play"))
      assertThat(isEnabled).isFalse()
    }
  }

  @Test
  fun enableHighApiPlayStorePhones() {
    PairDeviceAction(createAvdInfoProvider(SystemImage.DEFAULT_TAG, apiLevel = 30, hasPlayStore = true)).apply {
      assertThat(description).isEqualTo(message("wear.assistant.device.list.tooltip.ok"))
      assertThat(isEnabled).isTrue()
    }
  }

  private fun createAvdInfoProvider(idDisplay: IdDisplay, apiLevel: Int, hasPlayStore: Boolean): AvdInfoProvider {
    val avdInfo = Mockito.mock(AvdInfo::class.java)
    Mockito.`when`(avdInfo.androidVersion).thenReturn(AndroidVersion(apiLevel))
    Mockito.`when`(avdInfo.tag).thenReturn(idDisplay)
    Mockito.`when`(avdInfo.hasPlayStore()).thenReturn(hasPlayStore)

    val avdInfoProvider = Mockito.mock(AvdInfoProvider::class.java)
    Mockito.`when`(avdInfoProvider.avdInfo).thenReturn(avdInfo)

    return avdInfoProvider
  }
}