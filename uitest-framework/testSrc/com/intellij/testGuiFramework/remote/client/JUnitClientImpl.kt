// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.remote.client

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.remote.transport.*
import org.junit.runner.notification.Failure
import java.io.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * @author Sergey Karashevich
 */
class JUnitClientImpl(host: String, port: Int, initHandlers: Array<ClientHandler>? = null) : JUnitClient {
  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.remote.client.JUnitClientImpl")
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

  inner class ClientReceiveThread(private val connection: Socket, private val objectInputStream: ObjectInputStream) : Thread(RECEIVE_THREAD) {
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

  inner class ClientSendThread(private val connection: Socket, private val objectOutputStream: ObjectOutputStream) : Thread(SEND_THREAD) {
    override fun run() {
      try {
        while (!connection.isClosed) {
          val message = poolOfMessages.take()
          LOG.info("Sending message: $message")
          try {
            objectOutputStream.writeObject(message)
          } catch (e : IOException) {
            // if we tried to send a non-serializable Throwable, then wrap its string representation in an Exception and send that instead.
            if ((e is NotSerializableException || e is InvalidClassException) && message is JUnitInfoMessage && message.info is JUnitFailureInfo) {
              val info = message.info
              val serializableThrowable = Exception(info.failure.exception.toString())
              val serializableMessage = JUnitInfoMessage(JUnitFailureInfo(info.type, Failure(info.description, serializableThrowable)))
              objectOutputStream.writeObject(serializableMessage)
            } else {
              throw e
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

  inner class KeepAliveThread(private val connection: Socket, private val objectOutputStream: ObjectOutputStream) : Thread(KEEP_ALIVE_THREAD) {
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
          (ApplicationManager.getApplication() as ApplicationEx).exit(true, true)
        }
      }
    }
  }

}
