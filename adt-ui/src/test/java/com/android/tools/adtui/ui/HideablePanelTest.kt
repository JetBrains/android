/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.ui

import com.android.tools.adtui.TreeWalker
import org.junit.Test

import javax.swing.*
import java.util.regex.Pattern

import com.google.common.truth.Truth.assertThat

class HideablePanelTest {

  @Test
  fun testExpandChangesChildVisibility() {
    val childPanel = JPanel()
    val panel = HideablePanel.Builder("Title", childPanel).build()
    assertThat(childPanel.isVisible).isTrue()
    panel.isExpanded = false
    assertThat(childPanel.isVisible).isFalse()
    panel.isExpanded = true
    assertThat(childPanel.isVisible).isTrue()
  }

  @Test
  fun testExpandCallsCallback() {
    val childPanel = JPanel()
    val panel = HideablePanel.Builder("Title", childPanel).build()
    // Create array to make boolean accessible inside lambda.
    var expandedValue = false
    panel.addStateChangedListener { expandValue ->
      // Set value to passed value to validate it got called.
      expandedValue = panel.isExpanded
    }

    // Test false value
    panel.isExpanded = false
    assertThat(expandedValue).isFalse()
    // Test true value
    panel.isExpanded = true
    assertThat(expandedValue).isTrue()
  }

  @Test
  fun testShowSeparatorFalse() {
    val childPanel = JPanel()
    val panel = HideablePanel.Builder("Title", childPanel)
      .setShowSeparator(false).build()
    val treeWalker = TreeWalker(panel)
    val countOfSeparators = treeWalker.descendants().filterIsInstance<JSeparator>().size
    assertThat(countOfSeparators).isEqualTo(0)
  }

  @Test
  fun testInitiallyExpanded() {
    val childPanel = JPanel()
    val panel = HideablePanel.Builder("Title", childPanel)
      .setInitiallyExpanded(false).build()
    assertThat(childPanel.isVisible).isFalse()
  }

  @Test
  fun testNorthEastComponent() {
    val childPanel = JPanel()
    val northEastPanel = JPanel()
    val panel = HideablePanel.Builder("Title", childPanel)
      .setNorthEastComponent(northEastPanel).build()
    val treeWalker = TreeWalker(panel)
    val foundNorthEastComponent = treeWalker.descendantStream().filter { component -> component == northEastPanel }.count()
    assertThat(foundNorthEastComponent).isEqualTo(1)
  }

  @Test
  fun testLabelIsWrappedWithNoBr() {
    val childPanel = JPanel()
    val northEastPanel = JPanel()
    val brPattern = Pattern.compile("<html><nobr>(.*)</nobr></html>")
    val panel = HideablePanel.Builder("My Long Title", childPanel)
      .setNorthEastComponent(northEastPanel).build()
    val treeWalker = TreeWalker(panel)
    val label = treeWalker.descendants().filterIsInstance<JLabel>().first()
    val matcher = brPattern.matcher(label.text)
    assertThat(matcher.matches()).isTrue()
  }

}
