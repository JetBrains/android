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

import com.intellij.testGuiFramework.remote.transport.MessageType
import com.intellij.testGuiFramework.remote.transport.TransportMessage
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.junit.runner.Result
import org.junit.runner.notification.RunListener
import org.junit.runner.notification.RunNotifier
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
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
  private val postingMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()
  private val receivingMessages: BlockingQueue<TransportMessage> = LinkedBlockingQueue()
  private val LOG = Logger.getLogger("#com.intellij.testGuiFramework.remote.server.JUnitServerImpl")

  private val serverSocket = ServerSocket(0)
  lateinit private var serverSendThread: ServerSendThread
  lateinit private var serverReceiveThread: ServerReceiveThread
  lateinit private var connection: Socket
  private var isStarted = false

  lateinit private var objectInputStream: ObjectInputStream
  lateinit private var objectOutputStream: ObjectOutputStream

  private val IDE_STARTUP_TIMEOUT = 180000

  private val port: Int

  init {
    port = serverSocket.localPort
    LOG.level = Level.INFO
    LOG.info("Server running on port $port")
    serverSocket.soTimeout = IDE_STARTUP_TIMEOUT
    notifier.addListener(object : RunListener() {
      override fun testRunFinished(result: Result?) {
        send(TransportMessage(MessageType.CLOSE_IDE))
        super.testRunFinished(result)
      }
    })
  }

  override fun start() {
    connection = serverSocket.accept()
    LOG.info("Server accepted client on port: ${connection.port}")

    objectOutputStream = ObjectOutputStream(connection.getOutputStream())
    serverSendThread = ServerSendThread(connection, objectOutputStream)
    serverSendThread.start()

    objectInputStream = ObjectInputStream(connection.getInputStream())
    serverReceiveThread = ServerReceiveThread(connection, objectInputStream)
    serverReceiveThread.start()
    isStarted = true
  }

  override fun isStarted(): Boolean = isStarted

  override fun send(message: TransportMessage) {
    postingMessages.put(message)
    LOG.info("Add message to send pool: $message ")
  }

  override fun receive(): TransportMessage {
    return receivingMessages.poll(IDE_STARTUP_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
           ?: throw SocketException("Client doesn't respond. Either the test has hanged or IDE crushed.")
  }

  override fun isConnected(): Boolean {
    try {
      return connection.isConnected && !connection.isClosed
    }
    catch (lateInitException: UninitializedPropertyAccessException) {
      return false
    }
  }

  override fun getPort() = port

  override fun stopServer() {
    serverSendThread.objectOutputStream.close()
    LOG.info("Object output stream closed")
    serverSendThread.interrupt()
    LOG.info("Server Send Thread joined")
    serverReceiveThread.objectInputStream.close()
    LOG.info("Object input stream closed")
    serverReceiveThread.interrupt()
    LOG.info("Server Receive Thread joined")
    connection.close()
    isStarted = false
  }

  inner class ServerSendThread(val connection: Socket, val objectOutputStream: ObjectOutputStream) : Thread(SEND_THREAD) {

    override fun run() {
      LOG.info("Server Send Thread started")
      try {
        while (!connection.isClosed) {
          val message = postingMessages.take()
          LOG.info("Sending message: $message ")
          objectOutputStream.writeObject(message)
        }
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      catch (e: Exception) {
        if (e is InvalidClassException) LOG.error("Probably client is down:", e)
      }
      finally {
        objectOutputStream.close()
      }
    }

  }

  inner class ServerReceiveThread(val connection: Socket, val objectInputStream: ObjectInputStream) : Thread(RECEIVE_THREAD) {

    override fun run() {
      try {
        LOG.info("Server Receive Thread started")
        while (!connection.isClosed) {
          val obj = objectInputStream.readObject()
          LOG.info("Receiving message: $obj")
          assert(obj is TransportMessage)
          val message = obj as TransportMessage
          receivingMessages.put(message)
        }
      }
      catch (e: Exception) {
        if (e is InvalidClassException) LOG.error("Probably serialization error:", e)
      }
    }
  }
}
