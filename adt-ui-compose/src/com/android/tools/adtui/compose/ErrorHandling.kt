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

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CancellationException
import java.awt.Component

/**
 * Runs the given block, catching any error that occurs, except for CancellationException and
 * ControlFlowException. The exception is logged at error severity (generating a crash report) and a
 * dialog is shown.
 *
 * An uncaught exception in a Compose event handler will kill Compose's Recomposer, resulting in a
 * frozen, unresponsive UI. While it is better to avoid having uncaught exceptions to begin with, it
 * may be difficult to guarantee this if there is a large amount of code that may be potentially
 * executed. This provides a failsafe in case an exception slips through, resulting in similar
 * behavior to an uncaught exception in Swing.
 */
inline fun <reified LoggerT : Any> catchAndShowErrors(
  parent: Component? = null,
  message: String = "An unexpected error occurred. See idea.log for details.",
  title: String = "Error",
  block: () -> Unit,
) {
  try {
    block()
  } catch (e: Exception) {
    if (e is CancellationException || e is ControlFlowException) throw e
    logger<LoggerT>().error(e)
    Messages.showErrorDialog(parent, message, title)
  }
}

/**
 * Runs the given block, catching any error that occurs, except for CancellationException and
 * ControlFlowException. The exception is logged at error severity (generating a crash report).
 *
 * An uncaught exception in a Compose event handler will kill Compose's Recomposer, resulting in a
 * frozen, unresponsive UI. While it is better to avoid having uncaught exceptions to begin with, it
 * may be difficult to guarantee this if there is a large amount of code that may be potentially
 * executed. This provides a failsafe in case an exception slips through, resulting in similar
 * behavior to an uncaught exception in Swing.
 */
inline fun <reified LoggerT : Any> catchAndLogErrors(
  block: () -> Unit,
) {
  try {
    block()
  } catch (e: Exception) {
    if (e is CancellationException || e is ControlFlowException) throw e
    logger<LoggerT>().error(e)
  }
}
