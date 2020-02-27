/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.AndroidIoManager
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Alarm
import gnu.trove.TObjectLongHashMap
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.streams.toList

/**
 * Keeps track of Android Emulators running on the local machine under the current user account.
 */
class RunningEmulatorCatalog : Disposable.Parent {
  // TODO: Use WatchService instead of polling.
  @Volatile var emulators: Set<EmulatorController> = ImmutableSet.of()

  private val registrationDir = getRegistrationDirectory()
  private val fileNamePattern = Pattern.compile("pid_\\d+.ini")
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  @Volatile private var isDisposing = false
  private val updateLock = Object()
  @GuardedBy("updateLock")
  private var updateInProgress = false
  @GuardedBy("updateLock")
  private var lastUpdateStartTime: Long = 0
  @GuardedBy("updateLock")
  private var lastUpdateDuration: Long = 0
  @GuardedBy("updateLock")
  private var nextScheduledUpdateTime: Long = Long.MAX_VALUE
  @GuardedBy("updateLock")
  private var listeners: List<Listener> = emptyList()
  @GuardedBy("updateLock")
  private val updateIntervalsByListener = TObjectLongHashMap<Listener>()
  /** Long.MAX_VALUE means no updates. A negative value means that the update interval needs to be calculated. */
  @GuardedBy("updateLock")
  private var updateInterval: Long = Long.MAX_VALUE

  /**
   * Adds a listener that will be notified when new Emulators start and running Emulators shut down.
   * The [updateIntervalMillis] parameter determines the level of data freshness required by the listener.
   *
   * @param listener the listener to be notified
   * @param updateIntervalMillis a positive number of milliseconds
   */
  @AnyThread
  fun addListener(listener: Listener, updateIntervalMillis: Int) {
    require(updateIntervalMillis > 0)
    synchronized(updateLock) {
      listeners = listeners.plus(listener)
      updateIntervalsByListener.put(listener, updateInterval)
      if (updateIntervalMillis < updateInterval) {
        updateInterval = updateIntervalMillis.toLong()
      }
      if (!updateInProgress) {
        scheduleUpdate(updateInterval)
      }
    }
  }

  /**
   * Removes a listener add by the [addListener] method.
   */
  @AnyThread
  fun removeListener(listener: Listener) {
    synchronized(updateLock) {
      listeners = listeners.minus(listener)
      val interval = updateIntervalsByListener.remove(listener)
      if (interval == updateInterval) {
        updateInterval = -1
      }
    }
  }

  private fun scheduleUpdate(delay: Long) {
    synchronized(updateLock) {
      val updateTime = System.currentTimeMillis() + delay
      // Check if an update is already scheduled soon enough.
      if (nextScheduledUpdateTime > updateTime) {
        if (nextScheduledUpdateTime != Long.MAX_VALUE) {
          alarm.cancelAllRequests()
        }
        nextScheduledUpdateTime = updateTime
        alarm.addRequest({ update() }, delay)
      }
    }
  }

  @GuardedBy("updateLock")
  private fun scheduleUpdate() {
    val delay = getUpdateInterval()
    if (delay != Long.MAX_VALUE) {
      scheduleUpdate(max(delay, min(lastUpdateDuration * 2, 1000)))
    }
  }

  @GuardedBy("updateLock")
  private fun getUpdateInterval(): Long {
    if (updateInterval < 0) {
      var value = Long.MAX_VALUE
      for (interval in updateIntervalsByListener.values) {
        value = value.coerceAtMost(interval)
      }
      updateInterval = value
    }
    return updateInterval
  }

  fun updateNow() {
    synchronized(updateLock) {
      scheduleUpdate(0)
    }
  }

  private fun update() {
    if (isDisposing) return

    synchronized(updateLock) {
      if (updateInProgress) {
        return
      }
      updateInProgress = true
      nextScheduledUpdateTime = Long.MAX_VALUE
    }

    try {
      val start = System.currentTimeMillis()
      val files = Files.list(registrationDir).use {
        it.filter { fileNamePattern.matcher(it.fileName.toString()).matches() }.toList()
      }
      val oldEmulators = emulators.associateBy { it.emulatorId }
      val newEmulators = ConcurrentHashMap<EmulatorId, EmulatorController>()
      if (files.isNotEmpty() && !isDisposing) {
        val latch = CountDownLatch(files.size)
        val executor = AndroidIoManager.getInstance().getBackgroundDiskIoExecutor()
        for (file in files) {
          executor.submit {
            var emulator: EmulatorController? = null
            var created = false
            if (!isDisposing) {
              val emulatorId = readEmulatorInfo(file)
              if (emulatorId != null) {
                emulator = oldEmulators[emulatorId]
                if (emulator == null) {
                  emulator = EmulatorController(emulatorId, this)
                  created = true
                }
                if (!isDisposing) {
                  newEmulators[emulator.emulatorId] = emulator
                }
              }
            }

            latch.countDown()

            // Connect to the running Emulator asynchronously.
            if (emulator != null && created) {
              emulator.connect()
            }
          }
        }
        latch.await()
      }

      val removedEmulators = oldEmulators.minus(newEmulators.keys).values
      val addedEmulators = newEmulators.minus(oldEmulators.keys).values
      val listenersSnapshot: List<Listener>

      synchronized(updateLock) {
        lastUpdateStartTime = start
        lastUpdateDuration = System.currentTimeMillis() - start
        updateInProgress = false
        emulators = ImmutableSet.copyOf(newEmulators.values)
        listenersSnapshot = listeners
        if (!isDisposing) {
          scheduleUpdate()
        }
      }

      // Notify listeners.
      if (listenersSnapshot.isNotEmpty()) {
        for (emulator in addedEmulators) {
          for (listener in listenersSnapshot) {
            if (isDisposing) break
            listener.emulatorAdded(emulator)
          }
        }
        for (emulator in removedEmulators) {
          for (listener in listenersSnapshot) {
            if (isDisposing) break
            listener.emulatorRemoved(emulator)
          }
        }
      }

      // Dispose removed Emulators.
      for (emulator in removedEmulators) {
        Disposer.dispose(emulator)
      }
    }
    catch (ignore: IOException) {
    }
  }

  private fun readEmulatorInfo(file: Path): EmulatorId? {
    var grpcPort = 0
    var grpcCertificate: String? = null
    var avdId: String? = null
    var avdName: String? = null
    var serialPort = 0
    var adbPort = 0
    try {
      for (line in Files.readAllLines(file)) {
        when {
          line.startsWith("grpc.port=") -> {
            grpcPort = line.substring("grpc.port=".length).toInt()
          }
          line.startsWith("grpc.certificate=") -> {
            grpcCertificate = line.substring("grpc.certificate=".length)
          }
          line.startsWith("avd.id=") -> {
            avdId = line.substring("add.id=".length)
          }
          line.startsWith("avd.name=") -> {
            avdName = line.substring("avd.name=".length).replace('_', ' ')
          }
          line.startsWith("port.serial=") -> {
            serialPort = line.substring("port.serial=".length).toInt()
          }
          line.startsWith("port.adb=") -> {
            adbPort = line.substring("port.adb=".length).toInt()
          }
        }
      }
    }
    catch (ignore: IOException) {
    }
    catch (ignore: NumberFormatException) {
    }

    return if (grpcPort > 0 && grpcCertificate != null && avdId != null && avdName != null && serialPort != 0 && adbPort != 0) {
      EmulatorId(grpcPort, grpcCertificate, avdId, avdName, serialPort, adbPort, file.fileName.toString())
    }
    else {
      null
    }
  }

  private fun getRegistrationDirectory(): Path {
    val dirInfo =
      when {
        SystemInfo.isMac -> {
          DirDescriptor("HOME", "Library/Caches/TemporaryItems")
        }
        SystemInfo.isWindows -> {
          DirDescriptor("LOCALAPPDATA", "Temp")
        }
        else -> {
          DirDescriptor("XDG_RUNTIME_DIR", null)
        }
      }

    val base = System.getenv(dirInfo.environmentVariable)
    if (dirInfo.relativePath == null) {
      return Paths.get(base, REGISTRATION_DIRECTORY_RELATIVE_PATH)
    }
    return Paths.get(base, dirInfo.relativePath, REGISTRATION_DIRECTORY_RELATIVE_PATH)
  }

  override fun beforeTreeDispose() {
    isDisposing = true
  }

  override fun dispose() {
  }

  /**
   * Defines interface for an object that receives notifications when a connection to a running Emulator
   * is established or an Emulator shuts down.
   */
  interface Listener {
    /**
     * Called when a connection to a running Emulator is established. Must be quick to avoid delaying catalog updates.
     * Due to asynchronous nature of the call, it may happen after calling the [removeListener] method.
     */
    @AnyThread
    fun emulatorAdded(emulator: EmulatorController)

    /**
     * Called when an Emulator shuts down. Must be quick to avoid delaying catalog updates.
     * Due to asynchronous nature of the call, it may happen after calling the [removeListener] method.
     */
    @AnyThread
    fun emulatorRemoved(emulator: EmulatorController)
  }

  private class DirDescriptor(val environmentVariable: String, val relativePath: String?)

  companion object {
    @JvmStatic
    fun getInstance(): RunningEmulatorCatalog {
      return ServiceManager.getService(RunningEmulatorCatalog::class.java)
    }

    private const val REGISTRATION_DIRECTORY_RELATIVE_PATH = "avd/running"
  }
}
