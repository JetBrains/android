/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.adtui.swing

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndWait
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.awt.Point
import java.awt.Rectangle
import java.awt.Window
import javax.swing.JComponent
import javax.swing.RootPaneContainer

internal inline fun <reified T : Window> createFakeWindow(root: JComponent, parentDisposable: Disposable?): T {
  // A mock is used here because in a headless environment it is not possible to instantiate
  // Window or any of its subclasses due to checks in the Window constructor.
  val mockWindow = mock(T::class.java)
  wrapInFakeWindow(mockWindow, root, parentDisposable)
  return mockWindow
}

private fun wrapInFakeWindow(mockWindow: Window, root: JComponent, parentDisposable: Disposable?) {
  val components = arrayOf(root)
  whenever(mockWindow.treeLock).thenCallRealMethod()
  whenever(mockWindow.toolkit).thenReturn(fakeToolkit)
  whenever(mockWindow.isShowing).thenReturn(true)
  whenever(mockWindow.isVisible).thenReturn(true)
  whenever(mockWindow.isEnabled).thenReturn(true)
  whenever(mockWindow.isLightweight).thenReturn(true)
  whenever(mockWindow.isFocusableWindow).thenReturn(true)
  whenever(mockWindow.locationOnScreen).thenReturn(Point(0, 0))
  whenever(mockWindow.size).thenReturn(root.size)
  whenever(mockWindow.bounds).thenReturn(Rectangle(0, 0, root.width, root.height))
  whenever(mockWindow.ownedWindows).thenReturn(emptyArray())
  whenever(mockWindow.isFocused).thenReturn(true)
  whenever(mockWindow.getFocusTraversalKeys(anyInt())).thenCallRealMethod()
  whenever(mockWindow.components).thenReturn(components)
  whenever(mockWindow.graphics).thenCallRealMethod()
  if (mockWindow is RootPaneContainer) {
    whenever(mockWindow.contentPane).thenReturn(root)
  }
  ComponentAccessor.setPeer(mockWindow, FakeWindowPeer())
  ComponentAccessor.setParent(root, mockWindow)
  root.addNotify()
  if (parentDisposable != null) {
    Disposer.register(parentDisposable) { runInEdtAndWait { root.removeNotify() } }
  }
}

private val fakeToolkit = FakeUiToolkit()