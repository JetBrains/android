/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import java.awt.Component
import java.awt.Container
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.RootPaneContainer

/**
 * An implementation of the [RootPaneContainer] interface for use in headless tests.
 */
class HeadlessRootPaneContainer(content: Container) : RootPaneContainer {

  private val rootPane = JRootPane().apply {
    contentPane = content
    size = content.size
    glassPane = IdeGlassPaneImpl(this).apply { size = content.size }
  }

  override fun getRootPane(): JRootPane {
    return rootPane
  }

  override fun getContentPane(): Container {
    return rootPane.contentPane
  }

  override fun setContentPane(contentPane: Container) {
    throw UnsupportedOperationException()
  }

  override fun getGlassPane(): IdeGlassPaneImpl {
    return rootPane.glassPane as IdeGlassPaneImpl
  }

  override fun setGlassPane(glassPane: Component) {
    throw UnsupportedOperationException()
  }

  override fun getLayeredPane(): JLayeredPane? {
    return rootPane.layeredPane
  }

  override fun setLayeredPane(layeredPane: JLayeredPane) {
    rootPane.layeredPane = layeredPane
  }
}