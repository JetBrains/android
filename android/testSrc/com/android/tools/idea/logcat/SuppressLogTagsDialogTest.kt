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
package com.android.tools.idea.logcat

import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JCheckBox

@RunsInEdt
class SuppressLogTagsDialogTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var dialog: SuppressLogTagsDialog

  @Before
  fun setUp() {
    enableHeadlessDialogs(projectRule.fixture.testRootDisposable)
  }

  @After
  fun tearDown() {
    if (this::dialog.isInitialized) {
      Disposer.dispose(dialog.dialogWrapper.disposable)
    }
  }

  @Test
  fun showManageDialog() {
    val selectedTags = hashSetOf("Tag2", "Tag3", "Tag1")
    val unselectedTags = hashSetOf("NewTag3", "NewTag1", "NewTag2")
    dialog = SuppressLogTagsDialog.newManageTagsDialog(projectRule.project, selectedTags, unselectedTags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkBoxes = treeWalker.descendants().filterIsInstance<JCheckBox>()

      assertThat(checkBoxes.map { it.text }).containsExactlyElementsIn(selectedTags.sorted() + unselectedTags.sorted()).inOrder()
      assertThat(checkBoxes.groupBy({ it.isSelected }, { it.text }))
        .containsExactly(true, selectedTags.sorted(), false, unselectedTags.sorted())
    }
  }

  @Test
  fun showManageDialog_getSelectedTagsWithNoInteraction_returnsSelectedTags() {
    val selectedTags = hashSetOf("Tag2", "Tag3", "Tag1")
    val unselectedTags = hashSetOf("NewTag3", "NewTag1", "NewTag2")
    dialog = SuppressLogTagsDialog.newManageTagsDialog(projectRule.project, selectedTags, unselectedTags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.getSelectedTags()).containsExactlyElementsIn(selectedTags)
    }
  }

  @Test
  fun showManageDialog_getSelectedTagsUnselectAll_returnsNoTags() {
    val selectedTags = hashSetOf("Tag2", "Tag3", "Tag1")
    val unselectedTags = hashSetOf("NewTag3", "NewTag1", "NewTag2")
    dialog = SuppressLogTagsDialog.newManageTagsDialog(projectRule.project, selectedTags, unselectedTags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)

      treeWalker.descendants().filterIsInstance<JCheckBox>().forEach { it.isSelected = false }

      assertThat(dialog.getSelectedTags()).isEmpty()
    }
  }

  @Test
  fun showManageDialog_getSelectedTagsChangeSome_returnsCorrectTags() {
    val selectedTags = hashSetOf("Tag2", "Tag3", "Tag1")
    val unselectedTags = hashSetOf("NewTag3", "NewTag1", "NewTag2")
    dialog = SuppressLogTagsDialog.newManageTagsDialog(projectRule.project, selectedTags, unselectedTags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)

      treeWalker.descendants().filterIsInstance<JCheckBox>().first { it.text == "Tag2" }.isSelected = false
      treeWalker.descendants().filterIsInstance<JCheckBox>().first { it.text == "NewTag2" }.isSelected = true

      assertThat(dialog.getSelectedTags()).containsExactlyElementsIn(selectedTags - hashSetOf("Tag2") + hashSetOf("NewTag2"))
    }
  }

  @Test
  fun showConfirmDialog() {
    val tags = hashSetOf("Tag2", "Tag3", "Tag1")
    dialog = SuppressLogTagsDialog.newConfirmTagsDialog(projectRule.project, tags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)
      val checkBoxes = treeWalker.descendants().filterIsInstance<JCheckBox>()

      assertThat(checkBoxes.map { it.text }).containsExactlyElementsIn(tags.sorted()).inOrder()
      assertThat(checkBoxes.count { !it.isSelected }).isEqualTo(0)
    }
  }

  @Test
  fun showConfirmDialog_getSelectedTagsWithNoInteraction_returnsAllTags() {
    val tags = hashSetOf("Tag2", "Tag3", "Tag1")
    dialog = SuppressLogTagsDialog.newConfirmTagsDialog(projectRule.project, tags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) {
      assertThat(dialog.getSelectedTags()).containsExactlyElementsIn(tags)
    }
  }

  @Test
  fun showConfirmDialog_getSelectedTagsUnselectAll_returnsNoTags() {
    val tags = hashSetOf("Tag2", "Tag3", "Tag1")
    dialog = SuppressLogTagsDialog.newConfirmTagsDialog(projectRule.project, tags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)

      treeWalker.descendants().filterIsInstance<JCheckBox>().forEach { it.isSelected = false }

      assertThat(dialog.getSelectedTags()).isEmpty()
    }
  }

  @Test
  fun showConfirmDialog_getSelectedTagsUnselectSome_returnsCorrectTags() {
    val tags = hashSetOf("Tag2", "Tag3", "Tag1")
    dialog = SuppressLogTagsDialog.newConfirmTagsDialog(projectRule.project, tags)

    createModalDialogAndInteractWithIt(dialog.dialogWrapper::show) { dialogWrapper ->
      val treeWalker = TreeWalker(dialogWrapper.rootPane)

      treeWalker.descendants().filterIsInstance<JCheckBox>().first { it.text == "Tag2" }.isSelected = false

      assertThat(dialog.getSelectedTags()).containsExactlyElementsIn(tags.filter { it != "Tag2" })
    }
  }
}