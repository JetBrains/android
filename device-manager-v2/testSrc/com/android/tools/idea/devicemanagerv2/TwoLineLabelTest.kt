/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.tools.adtui.categorytable.TablePresentation
import com.android.tools.adtui.categorytable.TablePresentationManager
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.AppUIUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import javax.swing.UIManager

@RunsInEdt
class TwoLineLabelTest {
  private val themeManagerRule = ThemeManagerRule()

  @get:Rule
  val ruleChain =
    RuleChain.outerRule(ApplicationRule()).around(themeManagerRule).around(EdtRule())!!

  @Test
  fun changeTheme() {
    themeManagerRule.setDarkTheme(false)

    val panel = TwoLineLabel()
    panel.updateTablePresentation(
      TablePresentationManager(),
      TablePresentation(foreground = JBColor.BLACK, background = JBColor.WHITE, false),
    )

    assertThat(panel.line1Label.foreground).isEqualTo(JBColor.BLACK)
    assertThat(panel.line1Label.foreground.rgb and 0xFFFFFF).isEqualTo(0)
    assertThat(panel.line2Label.foreground).isEqualTo(TwoLineLabel.LINE2_COLOR)
    assertThat(panel.line2Label.foreground.rgb and 0xFFFFFF).isEqualTo(0x818594)
    assertThat(panel.background).isEqualTo(JBColor.WHITE)

    themeManagerRule.setDarkTheme(true)

    // This assertion is the same, but JBColor.BLACK means something different now:
    assertThat(panel.line1Label.foreground).isEqualTo(JBColor.BLACK)
    assertThat(panel.line1Label.foreground.rgb and 0xFFFFFF).isEqualTo(0xBBBBBB)
    assertThat(panel.line2Label.foreground).isEqualTo(TwoLineLabel.LINE2_COLOR)
    assertThat(panel.line2Label.foreground.rgb and 0xFFFFFF).isEqualTo(0x6F737A)
    assertThat(panel.background).isEqualTo(JBColor.WHITE)
  }
}

/**
 * Rule for testing changes between light and dark themes. Actually changing the themes the way the
 * IDE does it is highly problematic in headless unit tests; this changes the relevant bits needed
 * for this class.
 */
class ThemeManagerRule : ExternalResource() {

  private val defaults = mutableMapOf<String, Any?>("Label.foreground" to Gray.xBB)

  override fun before() {
    swapDefaults()
  }

  override fun after() {
    swapDefaults()
  }

  private fun swapDefaults() {
    defaults.iterator().forEach { entry ->
      val oldValue = UIManager.getDefaults().get(entry.key)
      UIManager.getDefaults().put(entry.key, entry.value)
      entry.setValue(oldValue)
    }
  }

  fun setDarkTheme(dark: Boolean) {
    AppUIUtil.updateForDarcula(dark)
  }
}
