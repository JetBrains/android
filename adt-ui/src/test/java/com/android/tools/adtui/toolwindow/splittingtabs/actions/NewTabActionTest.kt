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
package com.android.tools.adtui.toolwindow.splittingtabs.actions

import org.mockito.kotlin.mock
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.DumbAware
import org.junit.Test
import org.mockito.Mockito.verify

/**
 * Tests for [NewTabAction]
 */
class NewTabActionTest {
  @Test
  fun isDumbAware() {
    assertThat(NewTabAction(textSupplier = { "" }, createNewTab = {})).isInstanceOf(DumbAware::class.java)
  }

  @Test
  fun testPresentation() {
    val newTabAction = NewTabAction(textSupplier = { "Label" }, createNewTab = {})

    assertThat(newTabAction.templatePresentation.text).isEqualTo("Label")
    assertThat(newTabAction.templatePresentation.icon).isEqualTo(AllIcons.General.Add)
  }

  @Test
  fun actionPerformed() {
    val createNewTab = mock<() -> Unit>()
    val newTabAction = NewTabAction(textSupplier = { "" }, createNewTab = createNewTab)

    newTabAction.actionPerformed(mock())

    verify(createNewTab).invoke()
  }
}