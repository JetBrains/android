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

import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.testGuiFramework.launcher.GuiTestLauncher
import com.intellij.testGuiFramework.remote.server.JUnitServer
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder
import com.intellij.testGuiFramework.remote.transport.*
import org.apache.log4j.Level
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
  constructor(testClass: Class<*>, val myBuildSystem: TargetBuildSystem.BuildSystem = TargetBuildSystem.BuildSystem.GRADLE) : BlockJUnit4ClassRunner(testClass) {

  constructor(testClass: Class<*>) : this(testClass, TargetBuildSystem.BuildSystem.GRADLE)

  val criticalError = Ref<Boolean>(false)

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

    val server = JUnitServerHolder.getServer(notifier)

    try {
      if (!server.isRunning()) {
        server.launchIdeAndStart()
      }
      val jUnitTestContainer = JUnitTestContainer(method.declaringClass, method.name, buildSystem = myBuildSystem)
      server.send(RunTestMessage(jUnitTestContainer))
    }
    catch (e: Exception) {
      e.printStackTrace()
      eachNotifier.addFailure(e)
      eachNotifier.fireTestFinished()
      return
    }
    var testIsRunning = true
    while(testIsRunning) {
      val message = try {
        server.receive()
      } catch (e: SocketException) {
        LOG.warn(e.message)
        eachNotifier.addFailure(e)
        eachNotifier.fireTestFinished()
        return
      }
      when (message) {
        is JUnitInfoMessage ->
          when (message.info.type) {
            Type.STARTED -> eachNotifier.fireTestStarted()
            Type.ASSUMPTION_FAILURE -> eachNotifier.addFailedAssumption((message.info.obj as Failure).exception as AssumptionViolatedException)
            Type.IGNORED -> { eachNotifier.fireTestIgnored(); testIsRunning = false }
            Type.FAILURE -> eachNotifier.addFailure(message.info.obj as Throwable)
            Type.FINISHED -> {
              server.setIdeErrorFlag(message.info.ideError)
              eachNotifier.fireTestFinished()
              testIsRunning = false
            }
            else -> throw UnsupportedOperationException("Bad message type from client: $message.content.type")
          }
        is RestartIdeMessage -> {
          val ex = restartIdeAndServer(server, method, message.resumeTest)
          if (ex != null) {
            eachNotifier.addFailure(ex)
            eachNotifier.fireTestFinished()
            return
          }
          server.send(RunTestMessage(JUnitTestContainer(method.declaringClass, method.name, message.index, myBuildSystem)))
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

  private fun runOnClientSide(method: FrameworkMethod, notifier: RunNotifier) {
    LOG.info("Starting test: '${testClass.name}.${method.name}'")
    super.runChild(method, notifier)
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.framework.GuiTestRemoteRunner")

    init {
      LOG.setLevel(Level.INFO)
    }
  }
}
