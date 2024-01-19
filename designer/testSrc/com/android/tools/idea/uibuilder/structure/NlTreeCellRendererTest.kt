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
package com.android.tools.idea.uibuilder.structure

import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.util.MockNlComponent
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreePath
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class NlTreeCellRendererTest {

  @Suppress("MemberVisibilityCanBePrivate")
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun displayString() {
    val badgeHandler = mock(NlTreeBadgeHandler::class.java)
    val tree = Tree()
    val renderer = NlTreeCellRenderer(badgeHandler)
    val component =
      renderer.getTreeCellRendererComponent(tree, "text", false, false, true, 3, false)
        as JComponent
    val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
    assertThat(labels[0].text).isEqualTo("text")
    assertThat(labels[0].font.isItalic).isTrue()
  }

  @Test
  fun displayButton() {
    val badgeHandler = mock(NlTreeBadgeHandler::class.java)
    val panel = mock(NlVisibilityGutterPanel::class.java)
    val tree = NlComponentTree(projectRule.project, null, panel)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.EXPANDED_RENDERER, true)
    val button =
      MockNlComponent.create(
        ApplicationManager.getApplication().runReadAction<XmlTag> {
          XmlTagUtil.createTag(projectRule.project, "<Button></Button>").apply {
            putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module)
          }
        }
      )
    tree.setUI(
      object : BasicTreeUI() {
        override fun getLeftChildIndent() = 0

        override fun getRightChildIndent() = 0

        override fun getPathForRow(tree: JTree?, row: Int) = TreePath(arrayOf(button))
      }
    )
    val renderer = NlTreeCellRenderer(badgeHandler)
    val component =
      renderer.getTreeCellRendererComponent(tree, button, false, false, true, 3, false)
        as JComponent
    val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
    assertThat(labels[0].text).isEqualTo("Button")
    assertThat(labels[0].icon).isSameAs(StudioIcons.LayoutEditor.Palette.BUTTON)
  }

  @Test
  fun displayPreference() {
    val badgeHandler = mock(NlTreeBadgeHandler::class.java)
    val panel = mock(NlVisibilityGutterPanel::class.java)
    val tree = NlComponentTree(projectRule.project, null, panel)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.EXPANDED_RENDERER, true)
    val button =
      MockNlComponent.create(
        ApplicationManager.getApplication().runReadAction<XmlTag> {
          XmlTagUtil.createTag(projectRule.project, "<ListPreference></ListPreference>").apply {
            putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module)
          }
        }
      )
    tree.setUI(
      object : BasicTreeUI() {
        override fun getLeftChildIndent() = 0

        override fun getRightChildIndent() = 0

        override fun getPathForRow(tree: JTree?, row: Int) = TreePath(arrayOf(button))
      }
    )
    val renderer = NlTreeCellRenderer(badgeHandler)
    val component =
      renderer.getTreeCellRendererComponent(
        tree,
        button,
        selected = false,
        expanded = false,
        leaf = true,
        row = 3,
        hasFocus = false,
      ) as JComponent
    val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
    assertThat(labels[0].text).isEqualTo("ListPreference")
    assertThat(labels[0].icon).isSameAs(StudioIcons.LayoutEditor.Palette.VIEW)
  }

  @Test
  fun displayPreferenceCompat() {
    val badgeHandler = mock(NlTreeBadgeHandler::class.java)
    val panel = mock(NlVisibilityGutterPanel::class.java)
    val tree = NlComponentTree(projectRule.project, null, panel)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.EXPANDED_RENDERER, true)
    val button =
      MockNlComponent.create(
        ApplicationManager.getApplication().runReadAction<XmlTag> {
          XmlTagUtil.createTag(
              projectRule.project,
              "<SwitchPreferenceCompat></SwitchPreferenceCompat>",
            )
            .apply { putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module) }
        }
      )
    tree.setUI(
      object : BasicTreeUI() {
        override fun getLeftChildIndent() = 0

        override fun getRightChildIndent() = 0

        override fun getPathForRow(tree: JTree?, row: Int) = TreePath(arrayOf(button))
      }
    )
    val renderer = NlTreeCellRenderer(badgeHandler)
    val component =
      renderer.getTreeCellRendererComponent(
        tree,
        button,
        selected = false,
        expanded = false,
        leaf = true,
        row = 3,
        hasFocus = false,
      ) as JComponent
    val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
    assertThat(labels[0].text).isEqualTo("SwitchPreferenceCompat")
    assertThat(labels[0].icon).isSameAs(StudioIcons.LayoutEditor.Palette.VIEW)
  }

  @Test
  fun displayPreferenceAndroidTitle() {
    val badgeHandler = mock(NlTreeBadgeHandler::class.java)
    val panel = mock(NlVisibilityGutterPanel::class.java)
    val tree = NlComponentTree(projectRule.project, null, panel)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.EXPANDED_RENDERER, true)
    val button =
      MockNlComponent.create(
        ApplicationManager.getApplication().runReadAction<XmlTag> {
          XmlTagUtil.createTag(
              projectRule.project,
              "<SwitchPreferenceCompat android:title=\"Title\"></SwitchPreferenceCompat>",
            )
            .apply { putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module) }
        }
      )
    tree.setUI(
      object : BasicTreeUI() {
        override fun getLeftChildIndent() = 0

        override fun getRightChildIndent() = 0

        override fun getPathForRow(tree: JTree?, row: Int) = TreePath(arrayOf(button))
      }
    )
    val renderer = NlTreeCellRenderer(badgeHandler)
    val component =
      renderer.getTreeCellRendererComponent(
        tree,
        button,
        selected = false,
        expanded = false,
        leaf = true,
        row = 3,
        hasFocus = false,
      ) as JComponent
    val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
    assertThat(labels[0].text).isEqualTo("Title")
    assertThat(labels[0].icon).isSameAs(StudioIcons.LayoutEditor.Palette.VIEW)
  }

  @Test
  fun displayPreferenceAppTitle() {
    val badgeHandler = mock(NlTreeBadgeHandler::class.java)
    val panel = mock(NlVisibilityGutterPanel::class.java)
    val tree = NlComponentTree(projectRule.project, null, panel)
    UIUtil.putClientProperty(tree, ExpandableItemsHandler.EXPANDED_RENDERER, true)
    val button =
      MockNlComponent.create(
        ApplicationManager.getApplication().runReadAction<XmlTag> {
          XmlTagUtil.createTag(
              projectRule.project,
              "<SwitchPreferenceCompat app:title=\"Title\"></SwitchPreferenceCompat>",
            )
            .apply { putUserData(ModuleUtilCore.KEY_MODULE, projectRule.module) }
        }
      )
    tree.setUI(
      object : BasicTreeUI() {
        override fun getLeftChildIndent() = 0

        override fun getRightChildIndent() = 0

        override fun getPathForRow(tree: JTree?, row: Int) = TreePath(arrayOf(button))
      }
    )
    val renderer = NlTreeCellRenderer(badgeHandler)
    val component =
      renderer.getTreeCellRendererComponent(
        tree,
        button,
        selected = false,
        expanded = false,
        leaf = true,
        row = 3,
        hasFocus = false,
      ) as JComponent
    val labels = UIUtil.findComponentsOfType(component, JLabel::class.java)
    assertThat(labels[0].text).isEqualTo("Title")
    assertThat(labels[0].icon).isSameAs(StudioIcons.LayoutEditor.Palette.VIEW)
  }
}
