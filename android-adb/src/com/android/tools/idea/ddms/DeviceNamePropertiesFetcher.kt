/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.ddms

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.tools.idea.concurrency.addCallback
import com.android.tools.idea.concurrency.listenInPoolThread
import com.android.tools.idea.concurrency.whenAllComplete
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.concurrency.ThreadingAssertions
import java.util.concurrent.Callable
import java.util.concurrent.Future

/**
 * [DeviceNameProperties] retrieved by [IDevice.getSystemProperty] may be time consuming
 * In such case, this class can be used to get property without blocking thread
 * [DeviceNamePropertiesProvider] will return an empty [DeviceNameProperties] when value is not available.
 * At the same time, it check whether a retrieving task is running and start one if it's not.
 * After got value, it will be updated in propertiesMap and FutureCallback will be invoked.
 * It would work as normal propertiesMap for device if value has been retrieved successfully.
 *
 * @param uiCallback: a customized FutureCallback which is used to refresh UI component when ListenableFuture completed
 * @param parent: Disposable parent
 */
class DeviceNamePropertiesFetcher @VisibleForTesting constructor(private val parent: Disposable,
                                                                 private val uiCallback: FutureCallback<DeviceNameProperties>,
                                                                 private val isDisposed: (Disposable) -> Boolean) : Disposable by parent, DeviceNamePropertiesProvider {
  private val edtExecutor = EdtExecutorService.getInstance()
  private val taskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("DeviceNamePropertiesFetcher")
  private val defaultValue = DeviceNameProperties(null, null, null, null)
  // This cache is for ListenableFuture<DeviceNameProperties> and must be accessed by taskExecutor only
  // Tasks will be queued up and make sure run as single thread
  private val tasksMap = HashMap<IDevice, ListenableFuture<DeviceNameProperties>>()
  // This cache for DeviceNameProperties and should be accessed by UI thread only
  private val deviceNamePropertiesMap = HashMap<IDevice, DeviceNameProperties?>()
  private val myDeviceChangeListener = DeviceChangeListener()

  init {
    Disposer.register(parent, this)
    AndroidDebugBridge.addDeviceChangeListener(myDeviceChangeListener)
  }

  internal constructor(parent: Disposable) : this(parent, DefaultCallback())

  constructor(parent: Disposable, uiCallback: FutureCallback<DeviceNameProperties>) : this(parent, uiCallback, Disposer::isDisposed)

  @VisibleForTesting
  class DefaultCallback : FutureCallback<DeviceNameProperties> {
    /** Does nothing. Use [DeviceNamePropertiesFetcher.get] to get the properties. */
    override fun onSuccess(properties: DeviceNameProperties?) {
    }

    override fun onFailure(throwable: Throwable) {
      Logger.getInstance(DeviceNamePropertiesFetcher::class.java).warn(throwable)
    }
  }

  enum class ThreadType {
    EDT,
    TASK
  }

  private fun IDevice.getDeviceSystemProperties(): ListenableFuture<DeviceNameProperties> {
    val futures = listOf<Future<String>>(
      getSystemProperty(IDevice.PROP_DEVICE_MODEL),
      getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER),
      getSystemProperty(IDevice.PROP_BUILD_VERSION),
      getSystemProperty(IDevice.PROP_BUILD_API_LEVEL))

    @Suppress("UnstableApiUsage")
    return futures
      .listenInPoolThread(taskExecutor)
      .whenAllComplete()
      .call(
        Callable<DeviceNameProperties> { DeviceNameProperties(futures[0].get(), futures[1].get(), futures[2].get(), futures[3].get()) },
        MoreExecutors.directExecutor())
  }

  private fun isRetrieving(device: IDevice): Boolean {
    assertThreadMatch(ThreadType.TASK)
    val task = tasksMap[device]
    return task != null && !task.isDone
  }

  private fun startRetriever(device: IDevice) {
    assertThreadMatch(ThreadType.TASK)
    val task = device.getDeviceSystemProperties()
    task.addCallback(
      edtExecutor,
      { deviceNameProperties ->
        if (deviceNamePropertiesMap[device] != deviceNameProperties) {
          deviceNamePropertiesMap[device] = deviceNameProperties
          if (!isDisposed(this)) {
            uiCallback.onSuccess(deviceNameProperties)
          }
        }
      },
      { t ->
        if (!isDisposed(this)) {
          uiCallback.onFailure(t!!)
        }
      })
    tasksMap[device] = task
  }

  private fun assertThreadMatch(callBy: ThreadType) {
    val application = ApplicationManager.getApplication()
    if (application != null && !application.isUnitTestMode) {
      when (callBy) {
        ThreadType.EDT -> ThreadingAssertions.assertEventDispatchThread()
        ThreadType.TASK -> assert(!application.isDispatchThread) { "This operation is time consuming and must not be called on EDT" }
      }
    }
  }

  override fun get(device: IDevice): DeviceNameProperties {
    assertThreadMatch(ThreadType.EDT)

    if (isDisposed(this)) {
      Logger.getInstance(DeviceNamePropertiesFetcher::class.java).warn("DeviceNamePropertiesFetcher has been disposed")
      return defaultValue
    }

    val value = deviceNamePropertiesMap[device]
    // only taskExecutor will invoke retrieving DeviceNameProperties tasks
    taskExecutor.execute { if (!isRetrieving(device)) startRetriever(device) }
    return value ?: defaultValue
  }

  override fun dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(myDeviceChangeListener)
  }

  inner class DeviceChangeListener : AndroidDebugBridge.IDeviceChangeListener {
    override fun deviceConnected(device: IDevice) {}

    override fun deviceDisconnected(device: IDevice) {
      edtExecutor.execute {
        assertThreadMatch(ThreadType.EDT)
        deviceNamePropertiesMap.remove(device)
        tasksMap.remove(device);
      }
    }

    override fun deviceChanged(device: IDevice, changeMask: Int) {
    }
  }
}
