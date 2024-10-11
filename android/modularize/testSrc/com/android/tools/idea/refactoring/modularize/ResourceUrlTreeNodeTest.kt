/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.refactoring.modularize

import com.android.resources.ResourceUrl
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import icons.StudioIcons
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

class ResourceUrlTreeNodeTest {

  @Rule
  @JvmField
  val strict: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  @Test
  fun `render sets icon to Android file`() {
    val renderer = mock<ColoredTreeCellRenderer>()

    ResourceUrlTreeNode(mock()).render(renderer)

    verify(renderer).icon = StudioIcons.Shell.Filetree.ANDROID_FILE
  }

  @Test
  fun `render appends the resource URL's string representation with the node's text attributes`() {
    val renderer = mock<ColoredTreeCellRenderer>()

    val url = mock<ResourceUrl>()
    val stringRep = "<a string representation of a ResourceUrl>"
    whenever(url.toString()).thenReturn(stringRep)

    val node = spy(ResourceUrlTreeNode(url))
    val textAttr = mock<SimpleTextAttributes>()
    Mockito.doReturn(textAttr).whenever(node).textAttributes

    node.render(renderer)

    verify(renderer).append(stringRep, textAttr)
  }
}