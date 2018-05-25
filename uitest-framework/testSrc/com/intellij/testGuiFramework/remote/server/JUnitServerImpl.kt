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
package com.intellij.testGuiFramework.remote.server

import com.android.tools.idea.tests.gui.framework.GuiTests
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.launcher.GuiTestLauncher
import com.intellij.testGuiFramework.launcher.GuiTestOptions
import com.intellij.testGuiFramework.launcher.RestartPolicy
import com.intellij.testGuiFramework.remote.transport.CloseIdeMessage
import com.intellij.testGuiFramework.remote.transport.MessageFromClient
import com.intellij.testGuiFramework.remote.transport.MessageFromServer
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Karashevich
 */

class JUnitServerImpl(notifier: RunNotifier) : JUnitServer {

  private val SEND_THREAD = "JUnit Server Send Thread"
  private val RECEIVE_THREAD = "JUnit Server Receive Thread"
  private val postingMessages: BlockingQueue<MessageFromServer> = LinkedBlockingQueue()
  private val receivingMessages: BlockingQueue<MessageFromClient> = LinkedBlockingQueue()
  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.remote.server.JUnitServerImpl")

  private val serverSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
  lateinit private var serverSendThread: ServerSendThread
  lateinit private var serverReceiveThread: ServerReceiveThread
  lateinit private var connection: Socket
  private var running = false

  lateinit private var objectInputStream: ObjectInputStream
  lateinit private var objectOutputStream: ObjectOutputStream

  private val IDE_STARTUP_TIMEOUT = 40000   // ms
  private val MESSAGE_INTERVAL_TIMEOUT = if (GuiTestOptions.isDebug()) Long.MAX_VALUE else 15L // seconds

  private val port: Int

  private var ideError: Boolean = false

  init {
    port = serverSocket.localPort
    LOG.info("Server running on port $port")
    serverSocket.soTimeout = IDE_STARTUP_TIMEOUT
    notifier.addListener(object : RunListener() {
      var testFailed = false

      override fun testFailure(failure: Failure?) {
        testFailed = true
        failure?.exception?.let { LOG.warn(it) }
        super.testFailure(failure)
      }

      override fun testFinished(description: Description?) {
        super.testFinished(description)
        val shouldRestart = when (GuiTestOptions.getRestartPolicy()) {
          RestartPolicy.EACH_TEST -> true
          RestartPolicy.TEST_FAILURE -> testFailed || ideError
          RestartPolicy.IDE_ERROR -> ideError
        }
        if (shouldRestart) {
          closeIdeAndStop()
          launchIdeAndStart()
        }
        testFailed = false
        ideError = false
      }

      override fun testRunFinished(result: Result?) {
        closeIdeAndStop()
        super.testRunFinished(result)
      }
    })
  }

  private fun start() {
    postingMessages.clear()
    receivingMessages.clear()
    val startTime = System.currentTimeMillis()
    connection = serverSocket.accept()
    LOG.info("Server accepted client on port: ${connection.port} after ${System.currentTimeMillis() - startTime}ms")

    objectOutputStream = ObjectOutputStream(connection.getOutputStream())
    serverSendThread = ServerSendThread(connection, objectOutputStream)
    serverSendThread.start()

    objectInputStream = ObjectInputStream(connection.getInputStream())
    serverReceiveThread = ServerReceiveThread(connection, objectInputStream)
    serverReceiveThread.start()
    running = true
  }

  override fun send(message: MessageFromServer) {
    postingMessages.put(message)
  }

  override fun receive(): MessageFromClient {
    val message = receivingMessages.poll(MESSAGE_INTERVAL_TIMEOUT, TimeUnit.SECONDS)
    if (message != null) {
      return message
    } else {
      closeIdeAndStop()
      throw SocketException("Server hasn't received a message in $MESSAGE_INTERVAL_TIMEOUT seconds.")
    }
  }

  override fun isRunning(): Boolean = running

  override fun setIdeErrorFlag(value: Boolean) {
    ideError = value
  }

  private fun stopServer() {
    if (!running) return
    serverSendThread.objectOutputStream.close()
    serverSendThread.interrupt()
    serverReceiveThread.objectInputStream.close()
    serverReceiveThread.interrupt()
    connection.close()
    running = false
    LOG.info("Server stopped.")
  }

  private fun stopClient() {
    send(CloseIdeMessage())
    val process = GuiTestLauncher.process
    if (process != null && !process.waitFor(5, TimeUnit.SECONDS)) {
      LOG.warn("Client didn't shut down when asked nicely; shutting it down forcibly.")
      process.destroyForcibly()
    }
  }

  override fun launchIdeAndStart() {
    val configDir = GuiTests.getConfigDirPath()
    FileUtil.delete(configDir)
    FileUtil.ensureExists(configDir)
    GuiTestLauncher.runIde(port)
    start()
  }

  override fun closeIdeAndStop() {
    stopClient()
    stopServer()
  }

  inner class ServerSendThread(val connection: Socket, val objectOutputStream: ObjectOutputStream) : Thread(SEND_THREAD) {

    override fun run() {
      try {
        while (!connection.isClosed) {
          val message = postingMessages.take()
          LOG.debug("Sending message: $message ")
          objectOutputStream.writeObject(message)
        }
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      catch (e: Exception) {
        if (e is InvalidClassException) LOG.warn("Probably client is down:", e)
      }
      finally {
        objectOutputStream.close()
      }
    }

  }

  inner class ServerReceiveThread(val connection: Socket, val objectInputStream: ObjectInputStream) : Thread(RECEIVE_THREAD) {

    override fun run() {
      try {
        while (!connection.isClosed) {
          val message = objectInputStream.readObject() as MessageFromClient
          LOG.debug("Receiving message: $message")
          receivingMessages.put(message)
        }
      }
      catch (e: Exception) {
        if (e is InvalidClassException) LOG.warn("Probably serialization error:", e)
      }
    }
  }
}
