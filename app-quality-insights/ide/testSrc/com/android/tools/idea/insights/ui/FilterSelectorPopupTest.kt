/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.insights.ui

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.insights.InsightsProvider.Source
import com.android.tools.idea.insights.inspection.ConnectionFilter
import com.android.tools.idea.insights.inspection.ConnectionPreference
import com.android.tools.idea.insights.ui.FilterSelectorPopup.NoAvailableAppsBanner
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import javax.swing.JTextArea
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class FilterSelectorPopupTest {

  private val applicationRule = ApplicationRule()
  private val popupRule = JBPopupRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(applicationRule).around(popupRule)

  private val filter1 = ConnectionFilter("app1", "app1", mapOf(Source.FIREBASE to ConnectionPreference.PREFERRED))
  private val filter2 = ConnectionFilter("app2", "app2", mapOf(Source.FIREBASE to ConnectionPreference.MATCHING))
  private val filter3 = ConnectionFilter("app3", "app3", mapOf(Source.PLAY to ConnectionPreference.CONFIGURED))

  @Test
  fun `popup shows both suggested and all apps`() {
    runBlocking {
      val filters = listOf(filter1, filter2, filter3)
      val popup = FilterSelectorPopup(filters, filter1, this, {})
      val fakeUi = FakeUi(popup)

      val lists = fakeUi.findAllComponents<JBList<ConnectionFilter>>()
      assertThat(lists).hasSize(2)
      assertThat(lists[0].model.getElementAt(0)).isEqualTo(filter1)
      assertThat(lists[1].model.getElementAt(0)).isEqualTo(filter2)
      assertThat(lists[1].model.getElementAt(1)).isEqualTo(filter3)

      val labels = fakeUi.findAllComponents<SimpleColoredComponent> { it !is ResizedSimpleColoredComponent }
      assertThat(labels).hasSize(2)
      assertThat(labels.map { it.toString() }).containsExactly("Suggested apps for this project", "Other apps")
    }
  }

  @Test
  fun `popup shows empty other app when all connections are associated with a variant`() {
    runBlocking {
      val filters = listOf(filter1)
      val popup = FilterSelectorPopup(filters, filter1, this, {})
      val fakeUi = FakeUi(popup)

      val list = fakeUi.findComponent<JBList<ConnectionFilter>>()!!
      assertThat(list.model.getElementAt(0)).isEqualTo(filter1)

      val labels = fakeUi.findAllComponents<SimpleColoredComponent> { it !is ResizedSimpleColoredComponent }
      assertThat(labels).hasSize(3)
      assertThat(labels.map { it.toString() }).containsExactly("Suggested apps for this project", "Other apps", "No apps accessible to you")
    }
  }

  @Test
  fun `popup shows empty suggested apps when no connections are associated with a variant`() {
    runBlocking {
      val filters = listOf(filter3)
      val popup = FilterSelectorPopup(filters, filter3, this, {})
      val fakeUi = FakeUi(popup)

      val list = fakeUi.findComponent<JBList<ConnectionFilter>>()!!
      assertThat(list.model.getElementAt(0)).isEqualTo(filter3)

      val labels = fakeUi.findAllComponents<SimpleColoredComponent> { it !is ResizedSimpleColoredComponent }
      assertThat(labels).hasSize(3)
      assertThat(labels.map { it.toString() }).containsExactly("Suggested apps for this project", "No suggested apps", "All apps")
    }
  }

  @Test
  fun `popup shows empty state message when there are no apps at all`() {
    runBlocking {
      val popup = FilterSelectorPopup(emptyList(), null, this, {})
      val fakeUi = FakeUi(popup)

      val banner = fakeUi.findComponent<NoAvailableAppsBanner>()!!
      val textPane = FakeUi(banner).findComponent<JTextArea>()!!
      assertThat(textPane.text).isEqualTo("No apps available.")
    }
  }

  @Test
  fun `search term narrows down connections in the list`() {
    runBlocking {
      val filters = listOf(filter1, filter2, filter3)
      val popup = FilterSelectorPopup(filters, filter1, this, {})
      val fakeUi = FakeUi(popup)
      val searchBox = fakeUi.findAllComponents<SearchTextField>().single()
      val lists = fakeUi.findAllComponents<JBList<ConnectionFilter>>()

      searchBox.text = filter2.title
      assertThat(lists[0].itemsCount).isEqualTo(0)
      assertThat(lists[1].itemsCount).isEqualTo(1)
      assertThat(lists[1].model.getElementAt(0)).isEqualTo(filter2)

      searchBox.text = filter1.appId
      assertThat(lists[0].itemsCount).isEqualTo(1)
      assertThat(lists[1].itemsCount).isEqualTo(0)
      assertThat(lists[0].model.getElementAt(0)).isEqualTo(filter1)
    }
  }

  @Test
  fun `popup shows correct icons for apps`() {
    runBlocking {
      val filter4 =
        ConnectionFilter(
          "app4",
          "app4",
          mapOf(Source.FIREBASE to ConnectionPreference.PREFERRED, Source.PLAY to ConnectionPreference.PREFERRED),
        )
      val filters = listOf(filter1, filter2, filter3, filter4)
      val popup = FilterSelectorPopup(filters, filter1, this, {})
      val fakeUi = FakeUi(popup)

      val lists = fakeUi.findAllComponents<JBList<ConnectionFilter>>()
      assertThat(lists).hasSize(2)

      val list1 = lists[0]
      val list1Renderer1 = list1.cellRenderer.getListCellRendererComponent(list1, filter1, 0, false, false) as javax.swing.JPanel
      val list1Renderer1Icons = list1Renderer1.components[1] as javax.swing.JPanel
      assertThat(list1Renderer1Icons.components[0].isVisible).isTrue() // Firebase icon is visible
      assertThat(list1Renderer1Icons.components[1].isVisible).isFalse() // Play icon is invisible

      val list1Renderer2 = list1.cellRenderer.getListCellRendererComponent(list1, filter4, 1, false, false) as javax.swing.JPanel
      val list1Renderer2Icons = list1Renderer2.components[1] as javax.swing.JPanel
      assertThat(list1Renderer2Icons.components[0].isVisible).isTrue() // Firebase icon is visible
      assertThat(list1Renderer2Icons.components[1].isVisible).isTrue() // Play icon is visible

      val list2 = lists[1]
      val list2Renderer1 = list2.cellRenderer.getListCellRendererComponent(list2, filter2, 0, false, false) as javax.swing.JPanel
      val list2Renderer1Icons = list2Renderer1.components[1] as javax.swing.JPanel
      assertThat(list2Renderer1Icons.components[0].isVisible).isTrue() // Firebase icon is visible
      assertThat(list2Renderer1Icons.components[1].isVisible).isFalse() // Play icon is invisible

      val list2Renderer2 = list2.cellRenderer.getListCellRendererComponent(list2, filter3, 1, false, false) as javax.swing.JPanel
      val list2Renderer2Icons = list2Renderer2.components[1] as javax.swing.JPanel
      assertThat(list2Renderer2Icons.components[0].isVisible).isFalse() // Firebase icon is invisible
      assertThat(list2Renderer2Icons.components[1].isVisible).isTrue() // Play icon is visible
    }
  }
}
