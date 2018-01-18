// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.testGuiFramework.framework

import com.android.tools.idea.tests.gui.framework.IdeControl
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.fest.swing.timing.Wait

/**
 * This provides a convenient way to run tests that restart the IDE. Provide at least two functions as arguments; the client IDE will be
 * restarted between running one function and the next.
 *
 * It is strongly recommended that the @Test method for a test that restarts the IDE consist solely of an invocation of this method. The
 * method is invoked every time the IDE starts, so any other code present will execute multiple times, which is misleading to a reader and
 * is probably not what was intended. Also note that any @Before and @After methods will also be invoked multiple times.
 *
 * Internally, when the client sends a message with type [MessageType.RESTART_IDE_AND_RESUME], it additionally sends a String label (as the
 * 'content' field) indicating where the test should resume. The server then restarts the IDE, and sends it a message with type
 * [MessageType.RESUME_TEST], whose 'resumeLabel' field is set to that label. The [ClientHandler] that matches (testResumeHandler in
 * [GuiTestThread]) sets the [GuiTestOptions.RESUME_LABEL] system property to that label, which is read here via
 * [GuiTestOptions.getResumeInfo]. [GuiTestThread] also takes care of setting the RESUME_LABEL property to
 * [GuiTestOptions.FIRST_RUN_RESUME_LABEL] whenever a new test is started.
 */
fun restartIdeBetween (vararg funcs: () -> Unit) {
  if (funcs.size < 2) throw IllegalArgumentException("restartIdeBetween requires at least 2 parameters")
  System.setProperty(GuiTestOptions.NUM_TEST_SEGMENTS_KEY, funcs.size.toString())
  val index = GuiTestOptions.getResumeInfo().toInt()
  funcs[index]()
  if (index + 1 < funcs.size) {
    restartIdeAndResume("${index + 1}")
  }
}

private fun restartIdeAndResume(label: String) {
  IdeControl.restartMessage = TransportMessage(MessageType.RESTART_IDE_AND_RESUME, label)
}

fun isFirstRun(): Boolean = GuiTestOptions.getResumeInfo() == GuiTestOptions.FIRST_RUN_RESUME_LABEL
fun isLastRun(): Boolean = GuiTestOptions.getResumeInfo() == (GuiTestOptions.getNumTestSegments() - 1).toString()
