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
package com.intellij.testGuiFramework.impl

import com.android.tools.idea.tests.gui.framework.deleteAnalyticsFile
import com.android.tools.idea.tests.gui.framework.deleteConsentFile
import com.android.tools.idea.tests.gui.framework.restoreHomeDirectory
import com.android.tools.idea.tests.gui.framework.setupAnalyticsFile
import com.android.tools.idea.tests.gui.framework.setupConsentFile
import com.android.tools.idea.tests.gui.framework.setupHomeDirectory
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.remote.JUnitClientListener
import com.intellij.testGuiFramework.remote.client.ClientHandler
import com.intellij.testGuiFramework.remote.client.JUnitClient
import com.intellij.testGuiFramework.remote.client.JUnitClientImpl
import com.intellij.testGuiFramework.remote.transport.CloseIdeMessage
import com.intellij.testGuiFramework.remote.transport.JUnitInfoMessage
import com.intellij.testGuiFramework.remote.transport.JUnitTestContainer
import com.intellij.testGuiFramework.remote.transport.MessageFromServer
import com.intellij.testGuiFramework.remote.transport.RunTestMessage
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class GuiTestThread : Thread(GUI_TEST_THREAD_NAME) {

  private var testQueue: BlockingQueue<JUnitTestContainer> = LinkedBlockingQueue()
  private val core = JUnitCore()

  companion object {
    val GUI_TEST_THREAD_NAME = "GuiTest Thread"
    var client: JUnitClient? = null
  }

  override fun run() {
    client = JUnitClientImpl(host(), port(), createHandlers())

    core.addListener(JUnitClientListener({ jUnitInfo -> client!!.send(JUnitInfoMessage(jUnitInfo)) }))

    try {
      while (true) {
        runTest(testQueue.take())
      }
    }
    catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  private fun createHandlers(): Array<ClientHandler> {
    val testHandler = object : ClientHandler {
      override fun accept(message: MessageFromServer) = message is RunTestMessage

      override fun handle(message: MessageFromServer) {
        val content = (message as RunTestMessage).testContainer
        System.setProperty(GuiTestOptions.SEGMENT_INDEX, content.segmentIndex.toString())
        testQueue.add(content)
      }
    }

    val closeHandler = object : ClientHandler {
      override fun accept(message: MessageFromServer) = message is CloseIdeMessage

      override fun handle(message: MessageFromServer) {
        client?.stop()
      }
    }

    return arrayOf(testHandler, closeHandler)
  }

  private fun host(): String = System.getProperty(GuiTestStarter.GUI_TEST_HOST)

  private fun port(): Int = System.getProperty(GuiTestStarter.GUI_TEST_PORT).toInt()

  private fun runTest(testContainer: JUnitTestContainer) {
    setupConsentFile()
    setupHomeDirectory()
    setupAnalyticsFile()
    core.run(Request.method(testContainer.testClass, testContainer.methodName))
    deleteConsentFile()
    deleteAnalyticsFile()
    restoreHomeDirectory()
  }
}