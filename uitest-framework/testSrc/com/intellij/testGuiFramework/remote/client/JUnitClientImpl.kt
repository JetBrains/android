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
package com.intellij.testGuiFramework.remote.client

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.remote.transport.*
import org.junit.runner.notification.Failure
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class JUnitClientImpl(val host: String, val port: Int, initHandlers: Array<ClientHandler>? = null) : JUnitClient {

  private val LOG = Logger.getInstance(JUnitClientImpl::class.java)
  private val RECEIVE_THREAD = "JUnit Client Receive Thread"
  private val SEND_THREAD = "JUnit Client Send Thread"
  private val KEEP_ALIVE_THREAD = "JUnit Keep Alive Thread"

  private val connection: Socket
  private val clientConnectionTimeout = 60000 //in ms
  private val clientReceiveThread: ClientReceiveThread
  private val clientSendThread: ClientSendThread
  private val poolOfMessages: BlockingQueue<MessageFromClient> = LinkedBlockingQueue()

  private val objectInputStream: ObjectInputStream
  private val objectOutputStream: ObjectOutputStream
  private val handlers: ArrayList<ClientHandler> = ArrayList()

  private val keepAliveThread: KeepAliveThread

  init {
    if (initHandlers != null) handlers.addAll(initHandlers)
    LOG.warn("Client connecting to Server($host, $port) ...")
    connection = Socket()
    connection.connect(InetSocketAddress(InetAddress.getByName(host), port), clientConnectionTimeout)
    LOG.warn("Client connected to Server($host, $port) successfully")

    objectOutputStream = ObjectOutputStream(connection.getOutputStream())
    clientSendThread = ClientSendThread(connection, objectOutputStream)
    clientSendThread.start()

    objectInputStream = ObjectInputStream(connection.getInputStream())
    clientReceiveThread = ClientReceiveThread(connection, objectInputStream)
    clientReceiveThread.start()

    keepAliveThread = KeepAliveThread(connection, objectOutputStream)
    keepAliveThread.start()
  }

  override fun addHandler(handler: ClientHandler) {
    handlers.add(handler)
  }

  override fun removeHandler(handler: ClientHandler) {
    handlers.remove(handler)
  }

  override fun removeAllHandlers() {
    handlers.clear()
  }

  override fun send(message: MessageFromClient) {
    poolOfMessages.add(message)
  }

  override fun stop() {
    poolOfMessages.clear()
    handlers.clear()
    connection.close()
    keepAliveThread.cancel()
  }

  inner class ClientReceiveThread(val connection: Socket, val objectInputStream: ObjectInputStream) : Thread(RECEIVE_THREAD) {
    override fun run() {
      try{
        while (!connection.isClosed) {
          val message = objectInputStream.readObject() as MessageFromServer
          LOG.info("Received message: $message")
          handlers
            .filter { it.accept(message) }
            .forEach { it.handle(message) }
        }
      } catch (e: Throwable) {
        LOG.warn("Transport receiving message exception", e)
      } finally {
        objectInputStream.close()
        this@JUnitClientImpl.stop()
      }
    }
  }

  private class ShoutIntoVoidOutputStream: OutputStream() {
    override fun write(b: Int) {}
  }

  inner class ClientSendThread(val connection: Socket, val objectOutputStream: ObjectOutputStream) : Thread(SEND_THREAD) {
    override fun run() {
      try {
        while (!connection.isClosed) {
          val message = poolOfMessages.take()
          LOG.info("Sending message: $message")
          val throwawayStream = ObjectOutputStream(ShoutIntoVoidOutputStream())
          throwawayStream.use {
            try {
              // calling writeObject on a non-serializable object leaves the output stream in an indeterminate, inoperable state.
              // Try sending the object on a throwaway stream first - if it fails, the real stream is undamaged, and we can send
              // a sanitized version of it.
              throwawayStream.writeObject(message)
              objectOutputStream.writeObject(message)
            }
            catch (e: IOException) {
              // if we tried to send a non-serializable Throwable, then wrap its string representation in an Exception and send that instead.
              if ((e is NotSerializableException || e is InvalidClassException) && message is JUnitInfoMessage && message.info is JUnitFailureInfo) {
                val info = message.info
                val serializableThrowable = Exception(buildString {
                  val ex = message.info.failure.exception
                  appendLine(ex.toString())
                  appendLine(ex.stackTrace.joinToString(separator = "\n    at "))
                  var cause = ex.cause
                  while (cause != null) {
                    appendLine("Caused by $cause")
                    appendLine(cause.stackTrace.joinToString(separator = "\n    at "))
                    // Throwables use a self-referential cause to indicate "uninitialized", and null to indicate "unknown" or "nonexistent"
                    if (cause.cause === cause) break
                    cause = cause.cause
                  }
                })
                val serializableMessage = JUnitInfoMessage(JUnitFailureInfo(info.type, Failure(info.description, serializableThrowable)))
                objectOutputStream.writeObject(serializableMessage)
              }
              else {
                throw e
              }
            }
          }
        }
      }
      catch(e: InterruptedException) {
        Thread.currentThread().interrupt()
      }
      finally {
        objectOutputStream.close()
        this@JUnitClientImpl.stop()
      }
    }
  }

  inner class KeepAliveThread(val connection: Socket, private val objectOutputStream: ObjectOutputStream) : Thread(KEEP_ALIVE_THREAD) {
    private val myExecutor = Executors.newSingleThreadScheduledExecutor()
    private var hasCancelled = false
    override fun run() {
      myExecutor.scheduleWithFixedDelay(
        {
          if (!connection.isClosed) {
            objectOutputStream.writeObject(KeepAliveMessage())
          } else{
            LOG.warn("Connection broken, shutting down client")
            cancel()
          }
        }, 0L, 5, TimeUnit.SECONDS)
    }

    fun cancel() {
      synchronized(this) {
        if (!hasCancelled) {
          hasCancelled = true
          myExecutor.shutdownNow()
          objectOutputStream.close()
          System.exit(0)  // Unlike ApplicationImpl.exit, this will shut the client down even if the EDT is unresponsive.
        }
      }
    }
  }

}
