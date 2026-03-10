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
package com.android.tools.idea.testing.ui

import com.android.tools.idea.testing.ui.ToolWindowHeadlessManagerImpl.InternalDecoratorFactory
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.ContentManager
import java.awt.Container
import javax.swing.JPanel
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FakeInternalDecoratorFactory : InternalDecoratorFactory {
  private val treeLock: Any = JPanel().treeLock

  @Suppress("UnstableApiUsage")
  override fun createInternalDecorator(contentManager: ContentManager): InternalDecoratorImpl {
    val mockDecorator = mock<InternalDecoratorImpl>(defaultAnswer = CALLS_REAL_METHODS)
    try {
      val field = Container::class.java.getDeclaredField("component")
      field.isAccessible = true
      field.set(mockDecorator, ArrayList<Any>())
    } catch (e: Exception) {
      throw RuntimeException(e)
    }
    doAnswer { "" }.whenever(mockDecorator).toString() // To avoid NPE while debugging.
    doAnswer { treeLock }.whenever(mockDecorator).treeLock

    doAnswer { ToolWindowHeadlessManagerImpl.unsplit(contentManager, it.getArgument(0)) }.whenever(mockDecorator).unsplit(any())

    doAnswer { ToolWindowHeadlessManagerImpl.split(it.getArgument(0), it.getArgument(1), it.getArgument(2)) }
      .whenever(mockDecorator)
      .splitWithContent(any(), anyInt(), anyInt())

    doAnswer { false }.whenever(mockDecorator).isSplitUnsplitInProgress
    return mockDecorator
  }
}
