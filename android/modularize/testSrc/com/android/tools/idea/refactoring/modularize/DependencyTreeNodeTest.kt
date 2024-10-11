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

import com.google.common.truth.Truth.assertThat
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.BitUtil
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.awt.Color
import kotlin.random.Random

class DependencyTreeNodeTest {

  @Rule
  @JvmField
  val strict: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  // We can't use the nice Mockito.spy(<class>) syntax because there's no nullary constructor
  private fun spyDependencyTreeNode(userObject: Any, referenceCount: Int): DependencyTreeNode =
    Mockito.mock(DependencyTreeNode::class.java, // mock the abstract class
                 withSettings().useConstructor(userObject, referenceCount) // call the real constructor
                   .defaultAnswer(CALLS_REAL_METHODS)) // call real methods when not mocked out

  @Test
  fun `text attributes are regular when node is checked`() {
    val node = spyDependencyTreeNode(mock(), Random.nextInt())
    whenever(node.isChecked).thenReturn(true)

    assertThat(node.textAttributes).isEqualTo(SimpleTextAttributes.REGULAR_ATTRIBUTES)
  }

  @Test
  fun `text attributes are strikethrough when node is not checked`() {
    val node = spyDependencyTreeNode(mock(), Random.nextInt())
    whenever(node.isChecked).thenReturn(false)

    assertThat(node.textAttributes.isStrikeout).isTrue()
    assertThat(BitUtil.isSet(node.textAttributes.style, SimpleTextAttributes.STYLE_ITALIC)).isTrue()
    assertThat(node.textAttributes.fgColor).isNull()
  }

  @Test
  fun `renderReferenceCount does nothing if reference count is not more than 1`() {
    val renderer = mock<ColoredTreeCellRenderer>()
    val attributes = mock<SimpleTextAttributes>()

    val node = spyDependencyTreeNode(mock(), 1)
    node.renderReferenceCount(renderer, attributes)

    verifyNoInteractions(renderer)
    verifyNoInteractions(attributes)
  }

  @Test
  fun `renderReferenceCount appends the ref count with smaller italicized text when it's more than 1`() {
    val renderer = mock<ColoredTreeCellRenderer>()
    val attributes = mock<SimpleTextAttributes>()
    whenever(attributes.style).thenReturn(SimpleTextAttributes.STYLE_STRIKEOUT)
    val color = mock<Color>()
    whenever(attributes.fgColor).thenReturn(color)
    val derivedAttr = SimpleTextAttributes(
      attributes.style or
        SimpleTextAttributes.STYLE_ITALIC or
        SimpleTextAttributes.STYLE_SMALLER,
      color
    )

    val node = spyDependencyTreeNode(mock(), 2)
    node.renderReferenceCount(renderer, attributes)

    verify(renderer).append(eq(" (${node.referenceCount} usages)"), eq(derivedAttr))
    verifyNoMoreInteractions(renderer)
  }
}