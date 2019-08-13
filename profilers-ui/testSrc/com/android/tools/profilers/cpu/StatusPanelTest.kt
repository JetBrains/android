/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.profilers.cpu

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.Range
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JLabel


class StatusPanelTest {
  @Test
  fun abortButtonCreatedWithRightName() {
    val abortText = "Abort"
    val panel = StatusPanel(TestStatusPanelModel(), "Status", abortText)
    val button = getAbortbutton(panel)
    assertThat(button.text).isEqualTo(abortText)
  }

  @Test
  fun abortButtonEnabledCanBeToggled() {
    val panel = StatusPanel(TestStatusPanelModel(), "Status", "Abort")
    val button = getAbortbutton(panel)
    assertThat(button.isEnabled).isTrue()
    panel.setAbortButtonEnabled(false)
    assertThat(button.isEnabled).isFalse()
  }

  @Test
  fun rangeUpdatesTimeText() {
    val model = TestStatusPanelModel()
    val panel = StatusPanel(model, "Status", "Abort")
    model.testRange.max = TimeUnit.SECONDS.toNanos(8).toDouble()
    assertThat(panel.durationLabel.text).contains("8")
  }

  private fun getAbortbutton(panel: StatusPanel) = TreeWalker(panel)
    .descendants()
    .filterIsInstance<JButton>()
    .first()

}

class TestStatusPanelModel : StatusPanelModel {
  var abortCalled = false
  val testRange = Range(0.0, TimeUnit.SECONDS.toNanos(5).toDouble())
  override fun getConfigurationText(): String {
    return "Test"
  }

  override fun getRange(): Range {
    return testRange
  }

  override fun abort() {
    abortCalled = true
  }

}