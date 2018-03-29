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
package com.intellij.testGuiFramework.framework

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.testGuiFramework.impl.GuiTestThread
import com.intellij.testGuiFramework.launcher.GuiTestLauncher
import com.intellij.testGuiFramework.remote.server.JUnitServer
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.*
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.Assert
import org.junit.AssumptionViolatedException
import org.junit.internal.runners.model.EachTestNotifier
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError
import java.lang.reflect.InvocationTargetException
import java.net.SocketException

/**
 * [GuiTestRemoteRunner] serves as the JUnit runner on both the server and client side (test classes can only be annotated with one @[RunWith]
 * annotation). Its role on the server side is to:
 *   - ensure that a client is running, and if not, launch one (see [GuiTestLauncher])
 *   - send [TransportMessage]s to the client instructing it to run tests
 *   - accept responses from the client about the test results and notify the appropriate [RunNotifier]
 *   - restart the client IDE if requested by the client
 *
 * On the client side, it
 *   - delegates to [BlockJUnit4ClassRunner] to actually run the specified test
 *   - notifies the [RunNotifier] ([JUnitClientListener] in particular) when a test finishes, fails, or is ignored
 *   - sends a RESTART_IDE message back to the server if the IDE has fatal errors
 */
open class GuiTestRemoteRunner @Throws(InitializationError::class)
  constructor(testClass: Class<*>, val buildSystem: TargetBuildSystem.BuildSystem = TargetBuildSystem.BuildSystem.GRADLE) : BlockJUnit4ClassRunner(testClass) {

  constructor(testClass: Class<*>) : this(testClass, TargetBuildSystem.BuildSystem.GRADLE)

  val SERVER_LOG = Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestRemoteRunner[SERVER]")!!
  val criticalError = Ref<Boolean>(false)

  init {
    SERVER_LOG.level = Level.INFO
  }

  override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
    if (GuiTestStarter.isGuiTestThread())
      runOnClientSide(method, notifier)
    else
      runOnServerSide(method, notifier)
  }

  private fun runOnServerSide(method: FrameworkMethod, notifier: RunNotifier) {
    val description = this@GuiTestRemoteRunner.describeChild(method)
    val eachNotifier = EachTestNotifier(notifier, description)
    if (criticalError.get()) { eachNotifier.fireTestIgnored(); return }

    SERVER_LOG.info("Starting test on server side: ${testClass.name}#${method.name}")
    val server = JUnitServerHolder.getServer(notifier)

    try {
      if (!server.isRunning()) {
        server.launchIdeAndStart()
      }
      val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name, buildSystem = buildSystem)
      server.send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
    }
    catch (e: Exception) {
      SERVER_LOG.error(e)
      e.printStackTrace()
      notifier.fireTestIgnored(description)
      Assert.fail(e.message)
    }
    var testIsRunning = true
    while(testIsRunning) {
      val message = try {
        server.receive()
      } catch (e: SocketException) {
        LOG.warn(e.message)
        eachNotifier.fireTestIgnored()
        return
      }
      if (message.content is JUnitInfo && message.content.testClassAndMethodName == JUnitInfo.getClassAndMethodName(description)) {
        when (message.content.type) {
          Type.STARTED -> eachNotifier.fireTestStarted()
          Type.ASSUMPTION_FAILURE -> eachNotifier.addFailedAssumption((message.content.obj as Failure).exception as AssumptionViolatedException)
          Type.IGNORED -> { eachNotifier.fireTestIgnored(); testIsRunning = false }
          Type.FAILURE -> eachNotifier.addFailure(message.content.obj as Throwable)
          Type.FINISHED -> { eachNotifier.fireTestFinished(); testIsRunning = false }
          else -> throw UnsupportedOperationException("Bad message type from client: $message.content.type")
        }
      }
      if (message.type == MessageType.RESTART_IDE) {
        restartIdeAndServer(server, method)
        sendRunTestCommand(method, server)
      }
      if (message.type == MessageType.RESTART_IDE_AND_RESUME) {
        val additionalInfoLabel = message.content
        if (additionalInfoLabel !is Int) throw Exception("Additional info for a resuming test should be an Int!")
        val ex = restartIdeAndServer(server, method, true)
        if (ex != null) {
          eachNotifier.addFailure(ex)
          eachNotifier.fireTestFinished()
          testIsRunning = false
        } else {
          sendResumeTestCommand(method, server, additionalInfoLabel)
        }
      }
    }
  }

  // run methods in test class annotated with @BetweenRestarts
  private fun runBetweenRestartsMethods (method: FrameworkMethod) {
    for (m in method.declaringClass.methods.filter { it.isAnnotationPresent(BetweenRestarts::class.java) }) {
      m.invoke(m.declaringClass.newInstance())
    }
  }

  private fun restartIdeAndServer (server: JUnitServer, method: FrameworkMethod, forResume: Boolean = false): Throwable? {
    server.closeIdeAndStop()
    if (forResume) {
      try {
        runBetweenRestartsMethods(method)
      } catch (e: InvocationTargetException) {
        return e.targetException
      }
    }
    server.launchIdeAndStart()
    return null
  }

  private fun sendRunTestCommand(method: FrameworkMethod, server: JUnitServer) {
    val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name, buildSystem = buildSystem)
    server.send(TransportMessage(MessageType.RUN_TEST, jUnitTestContainer))
  }

  private fun sendResumeTestCommand(method: FrameworkMethod, server: JUnitServer, segmentIndex: Int) {
    val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name, segmentIndex = segmentIndex, buildSystem = buildSystem)
    server.send(TransportMessage(MessageType.RESUME_TEST, jUnitTestContainer))
  }

  private fun runOnClientSide(method: FrameworkMethod, notifier: RunNotifier) {
    try {
      LOG.info("Starting test: '${testClass.name}.${method.name}'")
      // if IDE has fatal errors from a previous test, request a restart
      if (GuiTests.fatalErrorsFromIde().isNotEmpty()) {
        val restartIdeMessage = TransportMessage(MessageType.RESTART_IDE, "IDE has fatal errors from previous test, let's start a new instance")
        GuiTestThread.client?.send(restartIdeMessage) ?: throw Exception("JUnitClient is accidentally null")
      } else {
          super.runChild(method, notifier)
      }
    } catch (e: Exception) {
      LOG.error(e)
      throw e
    }
  }

  companion object {
    private val LOG = Logger.getLogger("#com.intellij.testGuiFramework.framework.GuiTestRemoteRunner[CLIENT]")

    init {
      LOG.level = Level.INFO
    }
  }
}
