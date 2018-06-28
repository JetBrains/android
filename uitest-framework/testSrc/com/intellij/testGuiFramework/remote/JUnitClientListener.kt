/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.remote

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.intellij.testGuiFramework.framework.isFirstRun
import com.intellij.testGuiFramework.framework.isLastRun
import com.intellij.testGuiFramework.remote.transport.JUnitFailureInfo
import com.intellij.testGuiFramework.remote.transport.JUnitInfo
import com.intellij.testGuiFramework.remote.transport.Type
import org.junit.runner.Description
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * [JUnitClientListener] is responsible for listening to test events generated from the client and relaying that information back to the
 * server by sending a [TransportMessage] with a [JUnitInfo] object in its content field.
 *
 * @author Sergey Karashevich
 */
class JUnitClientListener(val sendObjectFun: (JUnitInfo) -> Unit) : RunListener() {

  override fun testStarted(description: Description?) {
    description ?: throw Exception("Unable to send notification to JUnitServer that test is starter due to null description!")
    //don't send start state to server if it is a resumed test
    if (isFirstRun()) sendObjectFun(JUnitInfo(Type.STARTED, description))
  }

  override fun testAssumptionFailure(failure: Failure?) {
    sendObjectFun(JUnitFailureInfo(Type.ASSUMPTION_FAILURE, failure!!))
    if (!isLastRun()) sendObjectFun(JUnitInfo(Type.FINISHED, failure.description, GuiTests.fatalErrorsFromIde().isNotEmpty()))
  }

  override fun testFailure(failure: Failure?) {
    sendObjectFun(JUnitFailureInfo(Type.FAILURE, failure!!))
    if (!isLastRun()) sendObjectFun(JUnitInfo(Type.FINISHED, failure.description, GuiTests.fatalErrorsFromIde().isNotEmpty()))
  }

  override fun testFinished(description: Description?) {
    if (isLastRun()) sendObjectFun(JUnitInfo(Type.FINISHED, description!!, GuiTests.fatalErrorsFromIde().isNotEmpty()))
  }

  override fun testIgnored(description: Description?) {
    sendObjectFun(JUnitInfo(Type.IGNORED, description!!))
  }

}