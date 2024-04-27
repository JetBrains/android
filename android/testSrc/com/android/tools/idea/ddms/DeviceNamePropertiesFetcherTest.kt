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

import com.android.ddmlib.IDevice
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.idea.ddms.DeviceNamePropertiesFetcher.DefaultCallback
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class ResultType {
  SUCCESS,
  FAIL
}

@RunWith(JUnit4::class)
internal class DeviceNamePropertiesFetcherTest {
  private val myDisposable = Disposer.newDisposable()
  private val manufacturer = "google"
  private val model = "nexus 4"
  private val buildVersion = "4.2"
  private val apiLevel = "17"

  @After
  fun tearDown() {
    Disposer.dispose(myDisposable)
  }

  private fun createDeviceNamePropertiesProvider(result: AtomicReference<ResultType>,
                                                 successLatch: List<CountDownLatch>,
                                                 failureLatch: CountDownLatch = CountDownLatch(1)): DeviceNamePropertiesFetcher {
    return DeviceNamePropertiesFetcher(myDisposable, object : FutureCallback<DeviceNameProperties> {
      var successCount = 0
      override fun onFailure(t: Throwable?) {
        result.set(ResultType.FAIL)
        failureLatch.countDown()
      }

      override fun onSuccess(properties: DeviceNameProperties?) {
        result.set(ResultType.SUCCESS)
        successLatch[successCount++].countDown()
      }
    })
  }

  private fun createDevice(manufacturer: ListenableFuture<String>,
                           model: ListenableFuture<String>,
                           buildVersion: ListenableFuture<String>,
                           apiLevel: ListenableFuture<String>): IDevice {
    val d = Mockito.mock(IDevice::class.java)
    whenever(d.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(manufacturer)
    whenever(d.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(model)
    whenever(d.getSystemProperty(IDevice.PROP_BUILD_VERSION)).thenReturn(buildVersion)
    whenever(d.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL)).thenReturn(apiLevel)
    return d
  }

  @Test
  fun getDefaultValue() {
    val d = createDevice(
      Futures.immediateFuture(manufacturer),
      Futures.immediateFuture(model),
      Futures.immediateFuture(buildVersion),
      Futures.immediateFuture(apiLevel))
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(AtomicReference(), listOf(CountDownLatch(1)))
    assertNull(deviceNamePropertiesProvider.get(d).model)
  }

  @Test
  fun getThisHasBeenDisposed() {
    // Arrange
    val fetcher = DeviceNamePropertiesFetcher(myDisposable, DefaultCallback(), { true })
    val device = Mockito.mock(IDevice::class.java)

    // Act
    val properties = fetcher.get(device)

    // Assert
    assertEquals(DeviceNameProperties(null, null, null, null), properties)
  }

  @Test
  fun getValueAvailableImmediate() {
    val d = createDevice(
      Futures.immediateFuture(manufacturer),
      Futures.immediateFuture(model),
      Futures.immediateFuture(buildVersion),
      Futures.immediateFuture(apiLevel))
    val result = AtomicReference<ResultType>()
    val countDownLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(countDownLatch))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.manufacturer)
    countDownLatch.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    assertEquals(DeviceNameProperties(model, manufacturer, buildVersion, apiLevel), deviceProperties)
    assertEquals(ResultType.SUCCESS, result.get())
  }

  @Test
  fun getDefaultValueUntilValueAvailable() {
    val virtualTimeScheduler = VirtualTimeScheduler()
    val scheduledExecutorService = MoreExecutors.listeningDecorator(virtualTimeScheduler)

    @Suppress("UnstableApiUsage")
    val d = createDevice(
      scheduledExecutorService.schedule(Callable { manufacturer }, 5, TimeUnit.SECONDS),
      scheduledExecutorService.schedule(Callable { model }, 5, TimeUnit.SECONDS),
      scheduledExecutorService.schedule(Callable { buildVersion }, 5, TimeUnit.SECONDS),
      scheduledExecutorService.schedule(Callable { apiLevel }, 5, TimeUnit.SECONDS))

    val result = AtomicReference<ResultType>()
    val countDownLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(countDownLatch))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.manufacturer)
    virtualTimeScheduler.advanceBy(5, TimeUnit.SECONDS)
    countDownLatch.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    assertEquals(DeviceNameProperties(model, manufacturer, buildVersion, apiLevel), deviceProperties)
    assertEquals(ResultType.SUCCESS, result.get())
  }

  @Test
  fun getExceptionIsThrownDuringRetrieve() {
    val d = createDevice(
      Futures.immediateFailedFuture(Exception("Fail")),
      Futures.immediateFailedFuture(Exception("Fail")),
      Futures.immediateFailedFuture(Exception("Fail")),
      Futures.immediateFailedFuture(Exception("Fail")))
    val result = AtomicReference<ResultType>()
    val successLatch = CountDownLatch(1)
    val failureLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(successLatch), failureLatch)
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.manufacturer)
    successLatch.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    failureLatch.await(2, TimeUnit.SECONDS)
    assertNull(deviceProperties.manufacturer)
    assertEquals(ResultType.FAIL, result.get())
  }

  @Test
  fun getDeviceUnauthorized() {
    val d = createDevice(
      Futures.immediateFuture(null),
      Futures.immediateFuture(null),
      Futures.immediateFuture(null),
      Futures.immediateFuture(null))
    val result = AtomicReference<ResultType>()
    val countDownLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(countDownLatch))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.buildVersion)
    countDownLatch.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.buildVersion)
    assertEquals(ResultType.SUCCESS, result.get())
  }

  @Test
  fun getDeviceUnauthorizedUntilAuthorized() {
    val d = Mockito.mock(IDevice::class.java)
    whenever(
      d.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(manufacturer))
    whenever(
      d.getSystemProperty(IDevice.PROP_DEVICE_MODEL))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(model))
      .thenReturn(Futures.immediateFuture(model))
    whenever(
      d.getSystemProperty(IDevice.PROP_BUILD_VERSION))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(buildVersion))
    whenever(
      d.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(null))
      .thenReturn(Futures.immediateFuture(apiLevel))
    val result = AtomicReference<ResultType>()
    val successLatch1 = CountDownLatch(1)
    val successLatch2 = CountDownLatch(1)
    val successLatch3 = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(successLatch1, successLatch2, successLatch3))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.buildVersion)
    successLatch1.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    assertNull(deviceProperties.buildVersion)
    assertEquals(ResultType.SUCCESS, result.get())

    successLatch2.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    assertEquals(DeviceNameProperties(model, null, null, null), deviceProperties)
    assertEquals(ResultType.SUCCESS, result.get())

    successLatch3.await(2, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    assertEquals(DeviceNameProperties(model, manufacturer, buildVersion, apiLevel), deviceProperties)
    assertEquals(ResultType.SUCCESS, result.get())
  }
}
