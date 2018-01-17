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
import com.android.testutils.VirtualTimeScheduler
import com.google.common.truth.Truth
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.util.Disposer
import junit.framework.TestCase
import org.junit.After
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class ResultType {
  SUCCESS,
  FAIL
}

class DeviceNamePropertiesProviderTest {
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
    return DeviceNamePropertiesFetcher(object : FutureCallback<DeviceNameProperties> {
      var successCount = 0
      override fun onFailure(t: Throwable?) {
        result.set(ResultType.FAIL)
        failureLatch.countDown()
      }

      override fun onSuccess(properties: DeviceNameProperties?) {
        result.set(ResultType.SUCCESS)
        successLatch[successCount++].countDown()
      }
    }, myDisposable)
  }

  private fun createDevice(manufacturer: Future<String>,
                           model: Future<String>,
                           buildVersion: Future<String>,
                           apiLevel: Future<String>): IDevice {
    val d = Mockito.mock(IDevice::class.java)
    Mockito.`when`(d.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER)).thenReturn(manufacturer)
    Mockito.`when`(d.getSystemProperty(IDevice.PROP_DEVICE_MODEL)).thenReturn(model)
    Mockito.`when`(d.getSystemProperty(IDevice.PROP_BUILD_VERSION)).thenReturn(buildVersion)
    Mockito.`when`(d.getSystemProperty(IDevice.PROP_BUILD_API_LEVEL)).thenReturn(apiLevel)
    return d
  }

  @Test
  fun testGet_DefaultValue() {
    val d = createDevice(
        Futures.immediateFuture(manufacturer),
        Futures.immediateFuture(model),
        Futures.immediateFuture(buildVersion),
        Futures.immediateFuture(apiLevel))
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(AtomicReference(), listOf(CountDownLatch(1)))
    Truth.assertThat(deviceNamePropertiesProvider.get(d).model).isNull()
  }

  @Test
  fun testGet_ValueAvailableImmediate() {
    val d = createDevice(
        Futures.immediateFuture(manufacturer),
        Futures.immediateFuture(model),
        Futures.immediateFuture(buildVersion),
        Futures.immediateFuture(apiLevel))
    val result = AtomicReference<ResultType>()
    val countDownLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(countDownLatch))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    Truth.assertThat(deviceProperties.manufacturer).isNull()
    countDownLatch.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    TestCase.assertEquals(manufacturer, deviceProperties.manufacturer)
    TestCase.assertEquals(model, deviceProperties.model)
    TestCase.assertEquals(buildVersion, deviceProperties.buildVersion)
    TestCase.assertEquals(apiLevel, deviceProperties.apiLevel)
    TestCase.assertEquals(ResultType.SUCCESS, result.get())
  }

  @Test
  fun testGet_DefaultValueUntilValueAvailable() {
    val virtualTimeScheduler = VirtualTimeScheduler()
    val d = createDevice(
        virtualTimeScheduler.schedule(Callable<String> { manufacturer }, 5, TimeUnit.SECONDS),
        virtualTimeScheduler.schedule(Callable<String> { model }, 5, TimeUnit.SECONDS),
        virtualTimeScheduler.schedule(Callable<String> { buildVersion }, 5, TimeUnit.SECONDS),
        virtualTimeScheduler.schedule(Callable<String> { apiLevel }, 5, TimeUnit.SECONDS))
    val result = AtomicReference<ResultType>()
    val countDownLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(countDownLatch))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    Truth.assertThat(deviceProperties.manufacturer).isNull()
    virtualTimeScheduler.advanceBy(5, TimeUnit.SECONDS)
    countDownLatch.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    TestCase.assertEquals(manufacturer, deviceProperties.manufacturer)
    TestCase.assertEquals(model, deviceProperties.model)
    TestCase.assertEquals(buildVersion, deviceProperties.buildVersion)
    TestCase.assertEquals(apiLevel, deviceProperties.apiLevel)
    TestCase.assertEquals(ResultType.SUCCESS, result.get())
  }

  @Test
  fun testGet_ExceptionIsThrownDuringRetrieve() {
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
    Truth.assertThat(deviceProperties.manufacturer).isNull()
    successLatch.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    failureLatch.await(1, TimeUnit.SECONDS)
    Truth.assertThat(deviceProperties.manufacturer).isNull()
    TestCase.assertEquals(ResultType.FAIL, result.get())
  }

  @Test
  fun testGet_DeviceUnauthorized() {
    val d = createDevice(
        Futures.immediateFuture(null),
        Futures.immediateFuture(null),
        Futures.immediateFuture(null),
        Futures.immediateFuture(null))
    val result = AtomicReference<ResultType>()
    val countDownLatch = CountDownLatch(1)
    val deviceNamePropertiesProvider = createDeviceNamePropertiesProvider(result, listOf(countDownLatch))
    var deviceProperties = deviceNamePropertiesProvider.get(d)
    Truth.assertThat(deviceProperties.buildVersion).isNull()
    countDownLatch.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    Truth.assertThat(deviceProperties.buildVersion).isNull()
    TestCase.assertEquals(ResultType.SUCCESS, result.get())
  }

  @Test
  fun testGet_DeviceUnauthorizedUntilAuthorized() {
    val d = Mockito.mock(IDevice::class.java)
    Mockito.`when`(
        d.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER))
        .thenReturn(Futures.immediateFuture(null))
        .thenReturn(Futures.immediateFuture(null))
        .thenReturn(Futures.immediateFuture(manufacturer))
    Mockito.`when`(
        d.getSystemProperty(IDevice.PROP_DEVICE_MODEL))
        .thenReturn(Futures.immediateFuture(null))
        .thenReturn(Futures.immediateFuture(model))
        .thenReturn(Futures.immediateFuture(model))
    Mockito.`when`(
        d.getSystemProperty(IDevice.PROP_BUILD_VERSION))
        .thenReturn(Futures.immediateFuture(null))
        .thenReturn(Futures.immediateFuture(null))
        .thenReturn(Futures.immediateFuture(buildVersion))
    Mockito.`when`(
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
    Truth.assertThat(deviceProperties.buildVersion).isNull()
    successLatch1.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    Truth.assertThat(deviceProperties.buildVersion).isNull()
    TestCase.assertEquals(ResultType.SUCCESS, result.get())

    successLatch2.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    TestCase.assertEquals(model, deviceProperties.model)
    Truth.assertThat(deviceProperties.manufacturer).isNull()
    Truth.assertThat(deviceProperties.buildVersion).isNull()
    Truth.assertThat(deviceProperties.apiLevel).isNull()
    TestCase.assertEquals(ResultType.SUCCESS, result.get())

    successLatch3.await(1, TimeUnit.SECONDS)
    deviceProperties = deviceNamePropertiesProvider.get(d)
    TestCase.assertEquals(manufacturer, deviceProperties.manufacturer)
    TestCase.assertEquals(model, deviceProperties.model)
    TestCase.assertEquals(buildVersion, deviceProperties.buildVersion)
    TestCase.assertEquals(apiLevel, deviceProperties.apiLevel)
    TestCase.assertEquals(ResultType.SUCCESS, result.get())
  }
}
