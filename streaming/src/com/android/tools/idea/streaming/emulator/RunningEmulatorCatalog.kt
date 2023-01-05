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
package com.android.tools.idea.streaming.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.AndroidIoManager
import com.android.tools.idea.flags.StudioFlags
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil.getTempDirectory
import com.intellij.openapi.util.text.StringUtil.parseInt
import com.intellij.util.Alarm
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.regex.Pattern
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.max
import kotlin.math.min

/**
 * Keeps track of Android Emulators running on the local machine under the current user account.
 */
class RunningEmulatorCatalog : Disposable.Parent {
  // TODO: Use WatchService instead of polling.
  @Volatile var emulators: Set<EmulatorController> = ImmutableSet.of()

  private val fileNamePattern = Pattern.compile("pid_\\d+.ini")
  private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  @Volatile private var isDisposing = false
  /** This lock is held for reading while an update is running. */
  private val updateLock = ReentrantReadWriteLock()
  private val dataLock = Object()
  @GuardedBy("dataLock")
  private var lastUpdateStartTime: Long = 0
  @GuardedBy("dataLock")
  private var lastUpdateDuration: Long = 0
  @GuardedBy("dataLock")
  private var nextScheduledUpdateTime: Long = Long.MAX_VALUE
  @GuardedBy("dataLock")
  private var listeners: List<Listener> = emptyList()
  @GuardedBy("dataLock")
  private val updateIntervalsByListener = Object2LongOpenHashMap<Listener>()
  /** Long.MAX_VALUE means no updates. A negative value means that the update interval needs to be calculated. */
  @GuardedBy("dataLock")
  private var updateInterval: Long = Long.MAX_VALUE
  @GuardedBy("dataLock")
  private var pendingFutures: MutableList<SettableFuture<Set<EmulatorController>>> = mutableListOf()
  @GuardedBy("dataLock")
  private var registrationDirectory: Path? = computeRegistrationDirectory()

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
    synchronized(dataLock) {
      listeners = listeners.plus(listener)
      updateIntervalsByListener[listener] = updateInterval
      if (updateIntervalMillis < updateInterval) {
        updateInterval = updateIntervalMillis.toLong()
      }
      scheduleUpdate(updateInterval)
    }
  }

  /**
   * Removes a listener added by the [addListener] method.
   */
  @AnyThread
  fun removeListener(listener: Listener) {
    synchronized(dataLock) {
      listeners = listeners.minus(listener)
      val interval = updateIntervalsByListener.removeLong(listener)
      if (interval == updateInterval) {
        updateInterval = -1
      }
    }
  }

  private fun scheduleUpdate(delay: Long) {
    synchronized(dataLock) {
      val updateTime = System.currentTimeMillis() + delay
      // Check if an update is already scheduled soon enough.
      if (nextScheduledUpdateTime > updateTime) {
        if (nextScheduledUpdateTime != Long.MAX_VALUE) {
          alarm.cancelAllRequests()
        }
        nextScheduledUpdateTime = updateTime
        alarm.addRequest({ updateLock.read { update() } }, delay)
      }
    }
  }

  @GuardedBy("dataLock")
  private fun scheduleUpdate() {
    val delay = getUpdateInterval()
    if (delay != Long.MAX_VALUE) {
      scheduleUpdate(max(delay, min(lastUpdateDuration * 2, 1000)))
    }
  }

  @GuardedBy("dataLock")
  private fun getUpdateInterval(): Long {
    if (updateInterval < 0) {
      var value = Long.MAX_VALUE
      val iter = updateIntervalsByListener.values.iterator()
      while (iter.hasNext()) {
        value = value.coerceAtMost(iter.nextLong())
      }
      updateInterval = value
    }
    return updateInterval
  }

  /**
   * Triggers an immediate update and returns a future for the updated set of running emulators.
   */
  fun updateNow(): ListenableFuture<Set<EmulatorController>> {
    synchronized(dataLock) {
      val future: SettableFuture<Set<EmulatorController>> = SettableFuture.create()
      pendingFutures.add(future)
      scheduleUpdate(0)
      return future
    }
  }

  @GuardedBy("updateLock.readLock()")
  private fun update() {
    if (isDisposing) return

    val futures: List<SettableFuture<Set<EmulatorController>>>
    val directory: Path

    synchronized(dataLock) {
      nextScheduledUpdateTime = Long.MAX_VALUE

      if (pendingFutures.isEmpty()) {
        futures = emptyList()
      }
      else {
        futures = pendingFutures
        pendingFutures = mutableListOf()
      }

      directory = registrationDirectory ?: return
    }

    try {
      val start = System.currentTimeMillis()
      val files = readDirectoryContents(directory)
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
                  if (StudioFlags.EMBEDDED_EMULATOR_TRACE_DISCOVERY.get()) {
                    thisLogger().info("Discovered emulator $emulatorId")
                  }
                  emulator = EmulatorController(emulatorId, this)
                  created = true
                }
                if (!isDisposing) {
                  newEmulators[emulator.emulatorId] = emulator
                }
              }
            }

            latch.countDown()

            // Connect to the running embedded Emulator asynchronously.
            if (emulator != null && created && emulator.emulatorId.isEmbedded) {
              emulator.connect()
            }
          }
        }
        latch.await()
      }

      val removedEmulators = oldEmulators.minus(newEmulators.keys).values
      val addedEmulators = newEmulators.minus(oldEmulators.keys).values
      val listenersSnapshot: List<Listener>

      synchronized(dataLock) {
        if (isDisposing) return
        lastUpdateStartTime = start
        lastUpdateDuration = System.currentTimeMillis() - start
        emulators = ImmutableSet.copyOf(newEmulators.values)
        listenersSnapshot = listeners
        for (future in futures) {
          future.set(emulators)
        }
        if (!isDisposing) {
          scheduleUpdate()
        }
      }

      // Notify listeners.
      if (listenersSnapshot.isNotEmpty()) {
        for (emulator in removedEmulators) {
          if (StudioFlags.EMBEDDED_EMULATOR_TRACE_DISCOVERY.get()) {
            thisLogger().info("Emulator ${emulator.emulatorId} stopped")
          }
          for (listener in listenersSnapshot) {
            if (isDisposing) break
            listener.emulatorRemoved(emulator)
          }
        }
        for (emulator in addedEmulators) {
          for (listener in listenersSnapshot) {
            if (isDisposing) break
            listener.emulatorAdded(emulator)
          }
        }
      }

      // Dispose removed Emulators.
      for (emulator in removedEmulators) {
        Disposer.dispose(emulator)
      }
    }
    catch (e: IOException) {
      thisLogger().error("Running Emulator detection failed", e)

      synchronized(dataLock) {
        for (future in futures) {
          future.setException(e)
        }
        if (!isDisposing) {
          // TODO: Implement exponential backoff for retries.
          scheduleUpdate()
        }
      }
    }
  }

  private fun readDirectoryContents(directory: Path): List<Path> {
    return try {
      Files.list(directory).use { stream ->
        stream.filter { fileNamePattern.matcher(it.fileName.toString()).matches() }.toList()
      }
    }
    catch (e: NoSuchFileException) {
      emptyList() // The registration directory hasn't been created yet.
    }
  }

  /**
   * Reads and interprets the registration file of an Emulator (pid_NNNN.ini).
   */
  private fun readEmulatorInfo(file: Path): EmulatorId? {
    var grpcPort = 0
    var grpcCertificate: String? = null
    var grpcToken: String? = null
    var avdId: String? = null
    var avdName: String? = null
    var avdFolder: Path? = null
    var serialPort = 0
    var adbPort = 0
    var commandLine = emptyList<String>()
    try {
      for (line in Files.readAllLines(file)) {
        when {
          line.startsWith("grpc.port=") -> {
            grpcPort = parseInt(line.substring("grpc.port=".length), 0)
          }
          line.startsWith("grpc.certificate=") -> {
            grpcCertificate = line.substring("grpc.certificate=".length)
          }
          line.startsWith("grpc.token=") -> {
            grpcToken = line.substring("grpc.token=".length)
          }
          line.startsWith("avd.id=") -> {
            avdId = line.substring("add.id=".length)
          }
          line.startsWith("avd.name=") -> {
            val name = line.substring("avd.name=".length)
            // TODO: Remove replace('_', ' ') after January 1, 2024. It was a workaround for b/208966801.
            avdName = if (name.contains(' ')) name else name.replace('_', ' ')
          }
          line.startsWith("avd.dir=") -> {
            avdFolder = Paths.get(line.substring("add.dir=".length))
          }
          line.startsWith("port.serial=") -> {
            serialPort = parseInt(line.substring("port.serial=".length), 0)
          }
          line.startsWith("port.adb=") -> {
            adbPort = parseInt(line.substring("port.adb=".length), 0)
          }
          line.startsWith("cmdline=") -> {
            commandLine = decodeCommandLine(line.substring ("cmdline=".length))
          }
        }
      }
    }
    catch (ignore: IOException) {
    }

    if (grpcPort <= 0 || avdId == null || avdName == null || avdFolder == null ||
        serialPort <= 0 && adbPort <= 0 || commandLine.isEmpty()) {
      return null
    }

    return EmulatorId(grpcPort = grpcPort, grpcCertificate = grpcCertificate, grpcToken = grpcToken,
                      avdId = avdId, avdName = avdName, avdFolder = avdFolder,
                      serialPort = serialPort, adbPort = adbPort, commandLine = commandLine,
                      registrationFileName = file.fileName.toString())
  }

  override fun beforeTreeDispose() {
    isDisposing = true

    // Shut down all embedded Emulators.
    synchronized(dataLock) {
      for (emulator in emulators) {
        if (emulator.emulatorId.isEmbedded) {
          emulator.shutdown()
        }
      }
    }
  }

  override fun dispose() {
  }

  /**
   * Replaces registration directory location for tests. Calling this method with a null argument
   * restores the original directory location.
   */
  @TestOnly
  fun overrideRegistrationDirectory(directory: Path?) {
    synchronized(dataLock) {
      listeners = emptyList()
      updateIntervalsByListener.clear()
      emulators = emptySet()
      registrationDirectory = directory ?: computeRegistrationDirectory()
    }

    updateLock.write {} // Make sure that previously running updates have finished.
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

  companion object {
    @JvmStatic
    fun getInstance(): RunningEmulatorCatalog {
      return ApplicationManager.getApplication().getService(RunningEmulatorCatalog::class.java)
    }

    @JvmStatic
    private fun computeRegistrationDirectory(): Path? {
      val container = computeRegistrationDirectoryContainer()
      if (container == null) {
        thisLogger().error("Unable to determine Emulator registration directory")
      }
      return container?.resolve(REGISTRATION_DIRECTORY_RELATIVE_PATH)
    }

    /**
     * Returns the Emulator registration directory.
     */
    @JvmStatic
    private fun computeRegistrationDirectoryContainer(): Path? {
      when {
        SystemInfo.isMac -> {
          return resolvePath("{HOME}/Library/Caches/TemporaryItems")
        }
        SystemInfo.isWindows -> {
          return resolvePath("{LOCALAPPDATA}/Temp")
        }
        else -> { // Linux and Chrome OS.
          for (pattern in arrayOf("{XDG_RUNTIME_DIR}", "/run/user/{UID}", "{HOME}/.android")) {
            val dir = resolvePath(pattern) ?: continue
            if (Files.isDirectory(dir)) {
              return dir
            }
          }

          return resolvePath("${getTempDirectory()}/android-{USER}")
        }
      }
    }

    /**
     * Substitutes values of environment variables in the given [pattern] and returns the corresponding [Path].
     * Names of environment variables are enclosed in curly braces.
     */
    @JvmStatic
    private fun resolvePath(pattern: String): Path? {
      val result = StringBuilder()
      val name = StringBuilder()
      var braceDepth = 0
      for (c in pattern) {
        when {
          c == '{' -> braceDepth++
          c == '}' -> {
            if (--braceDepth == 0 && name.isNotEmpty()) {
              val value = getEnvironmentVariable(name.toString())
              result.append(value)
              name.clear()
            }
          }
          braceDepth > 0 -> name.append(c)
          else -> result.append(c)
        }
      }
      return Paths.get(result.toString())
    }

    @JvmStatic
    private fun getEnvironmentVariable(name: String): String? {
      val value = System.getenv(name)
      if (value == null && name == "UID") {
        // If the UID environment variable is not defined, obtain the user ID by alternative means.
        return getUid()
      }
      return value
    }

    @JvmStatic
    private fun getUid(): String? {
      try {
        val userName = System.getProperty("user.name")
        val command = "id -u $userName"
        val process = Runtime.getRuntime().exec(command)
        process.inputStream.use {
          val result = String(it.readBytes(), UTF_8).trim()
          if (result.isEmpty()) {
            return null
          }
          return result
        }
      }
      catch (e: IOException) {
        return null
      }
    }

    private const val REGISTRATION_DIRECTORY_RELATIVE_PATH = "avd/running"
  }
}
