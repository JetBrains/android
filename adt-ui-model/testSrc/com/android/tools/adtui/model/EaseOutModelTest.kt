/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.model

import com.android.tools.adtui.model.updater.FakeUpdater
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EaseOutModelTest {
  @Test
  fun testModelRemovedFromUpdater() {
    val timer = FakeTimer()
    val updater = FakeUpdater(timer)
    val model = EaseOutModel(updater, FakeTimer.ONE_SECOND_IN_NS)
    assertThat(updater.updatables).contains(model)

    // After 1 second, fade out should start the next update.
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(updater.updatables).contains(model)
    assertThat(model.ratioComplete).isWithin(0f).of(0f)

    // 1st update would start lerping the fade ratio
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(updater.updatables).contains(model)

    // 2nd update to clamp the ratio all the way to zero as it's close enough to the threshold
    timer.tick(FakeTimer.ONE_SECOND_IN_NS)
    assertThat(updater.updatables).doesNotContain(model)
    assertThat(model.ratioComplete).isWithin(0f).of(1f)
  }
}