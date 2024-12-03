/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import com.intellij.openapi.application.ApplicationManager
import java.awt.EventQueue
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain

/**
 * Replaces the EdtCoroutineDispatcher with a very simple dispatcher that doesn't depend on
 * IntelliJ.
 *
 * This is for use in standalone demo / interactive testing apps that would otherwise be broken by
 * the presence of EdtCoroutineDispatcher on the classpath without the IntelliJ Platform being used.
 */
fun installStandaloneMainDispatcher() {
  if (ApplicationManager.getApplication() != null) {
    throw IllegalStateException("This is only for use in standalone apps")
  }
  @OptIn(ExperimentalCoroutinesApi::class) Dispatchers.setMain(StandaloneMainDispatcher)
}

/** Wrapper around singleWindowApplication that invokes [installStandaloneMainDispatcher] first. */
fun standaloneSingleWindowApplication(
  state: WindowState = WindowState(),
  visible: Boolean = true,
  title: String = "Untitled",
  icon: Painter? = null,
  undecorated: Boolean = false,
  transparent: Boolean = false,
  resizable: Boolean = true,
  enabled: Boolean = true,
  focusable: Boolean = true,
  alwaysOnTop: Boolean = false,
  onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
  onKeyEvent: (KeyEvent) -> Boolean = { false },
  exitProcessOnExit: Boolean = true,
  content: @Composable FrameWindowScope.() -> Unit,
) {
  installStandaloneMainDispatcher()
  singleWindowApplication(
    state = state,
    visible = visible,
    title = title,
    icon = icon,
    undecorated = undecorated,
    transparent = transparent,
    resizable = resizable,
    enabled = enabled,
    focusable = focusable,
    alwaysOnTop = alwaysOnTop,
    onPreviewKeyEvent = onPreviewKeyEvent,
    onKeyEvent = onKeyEvent,
    exitProcessOnExit = exitProcessOnExit,
    content = content,
  )
}

private object StandaloneMainDispatcher : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    SwingUtilities.invokeLater(block)
  }

  override fun isDispatchNeeded(context: CoroutineContext): Boolean = !EventQueue.isDispatchThread()
}
