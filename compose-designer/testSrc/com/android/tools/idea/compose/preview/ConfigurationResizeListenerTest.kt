/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.ConfigurationListener.CFG_ACTIVITY
import com.android.tools.configurations.ConfigurationListener.CFG_NIGHT_MODE
import com.android.tools.configurations.ConfigurationModelModule
import com.android.tools.configurations.ConfigurationSettings
import com.android.tools.configurations.ResourceResolverCache
import com.android.tools.configurations.updateScreenSize
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneRenderConfiguration
import com.google.common.collect.ImmutableList
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class ConfigurationResizeListenerTest {

  private val layoutlibSceneManagerConfiguration = mock<LayoutlibSceneRenderConfiguration>()
  val configurationSettings = TestConfigurationSettingsImpl()

  private var showDecoration = false
    set(value) {
      whenever(layoutlibSceneManagerConfiguration.showDecorations).thenReturn(value)
      field = value
    }

  @Test
  fun `listener ignores changes without CFG_DEVICE`() = runTest {
    val sceneManager = mock<LayoutlibSceneManager>()
    val configuration = createConfiguration(500, 600)
    val dispatcher = StandardTestDispatcher(testScheduler)
    val listener =
      ConfigurationResizeListener(sceneManager, configuration, dispatcher).also {
        advanceUntilIdle()
      }

    listener.changed(CFG_NIGHT_MODE or CFG_ACTIVITY) // No CFG_DEVICE
    advanceUntilIdle()

    verify(sceneManager, never()).viewObject
    verify(sceneManager, never()).requestRenderWithNewSize(any(), any())
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `listener triggers render with showDecoration true`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = true
      }
    val configuration = createConfiguration(500, 600)

    val listener =
      ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
        .also { advanceUntilIdle() }
    configuration.addListener(listener)

    configuration.updateScreenSize(700, 800)
    advanceUntilIdle()

    verify(sceneManager, times(1)).requestRenderWithNewSize(700, 800)
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `listener triggers render with swapped x and y due to landscape orientation`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = true
      }
    val configuration = createConfiguration(500, 600)

    val listener =
      ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
        .also { advanceUntilIdle() }
    configuration.addListener(listener)

    val newDevice = device(500, 1000)
    val state =
      newDevice.defaultState.deepCopy()!!.apply { orientation = ScreenOrientation.LANDSCAPE }
    configuration.setEffectiveDevice(newDevice, state)
    advanceUntilIdle()

    verify(sceneManager, times(1)).requestRenderWithNewSize(1000, 500)
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `listener modifies LayoutParams and triggers render with showDecoration false`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = false
      }
    val configuration = createConfiguration(500, 600)
    val testView = TestView()

    whenever(sceneManager.viewObject).thenReturn(testView)
    whenever(sceneManager.executeInRenderSessionAsync(any(), any(), any())).then {
      val callback = it.getArgument(0, Runnable::class.java)
      callback.run()
      CompletableFuture.completedFuture(null)
    }

    val listener =
      ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
        .also { advanceUntilIdle() }

    configuration.addListener(listener)

    configuration.updateScreenSize(700, 800)
    advanceUntilIdle()

    verify(sceneManager, times(1)).requestRenderWithNewSize(700, 800)
    assertEquals(700, testView.getLayoutParams().width) // Check width changed by reflection
    assertEquals(800, testView.getLayoutParams().height) // Check height changed by reflection
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `listener handles null viewObject gracefully`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = false
      }
    val configuration = createConfiguration(500, 600)

    whenever(sceneManager.viewObject).thenReturn(null) // Return null viewObject

    val listener =
      ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
        .also { advanceUntilIdle() }
    configuration.addListener(listener)
    configuration.updateScreenSize(700, 800)

    advanceUntilIdle()

    verify(sceneManager, times(1)).viewObject
    verify(sceneManager, never()).requestRenderWithNewSize(any(), any())
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `executeInRenderSession cancels on rapid updates test`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = false
      }
    val configuration = createConfiguration(500, 600)
    val testView = TestView()
    val firstRenderBlockStarted = CompletableDeferred<Unit>()
    val allowFirstRenderToComplete = CompletableDeferred<Unit>()

    whenever(sceneManager.executeInRenderSessionAsync(any(), any(), any())).thenAnswer {
      val callback = it.getArgument<Runnable>(0)
      if (!firstRenderBlockStarted.isCompleted) {
        firstRenderBlockStarted.complete(Unit) // Signal that the block has started.
      }
      callback.run()
      allowFirstRenderToComplete.asCompletableFuture()
    }

    whenever(sceneManager.viewObject).thenReturn(testView)

    val listener =
      ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
        .also { advanceUntilIdle() }

    configuration.addListener(listener)

    // Simulate multiple, rapid device changes.
    configuration.updateScreenSize(700, 800)
    runCurrent()
    firstRenderBlockStarted.await()
    configuration.updateScreenSize(900, 1000)
    allowFirstRenderToComplete.complete(Unit)
    advanceUntilIdle()

    verify(sceneManager, times(2)).executeInRenderSessionAsync(any(), any(), any())
    verify(sceneManager, never()).requestRenderWithNewSize(700, 800)
    verify(sceneManager, times(1)).requestRenderWithNewSize(900, 1000)
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `multiple device changes are handled sequentially`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = true
      }
    val configuration = createConfiguration(500, 600)
    val dispatcher = StandardTestDispatcher(testScheduler)
    val listener =
      ConfigurationResizeListener(sceneManager, configuration, dispatcher).also {
        advanceUntilIdle()
      }
    configuration.addListener(listener)

    // Simulate multiple, rapid device changes.
    configuration.updateScreenSize(700, 800)
    advanceUntilIdle()
    configuration.updateScreenSize(900, 1000)
    advanceUntilIdle()
    configuration.updateScreenSize(1100, 1200)
    advanceUntilIdle()

    // Verify that requestRenderWithNewSize was called for each size.
    verify(sceneManager, times(1)).requestRenderWithNewSize(700, 800)
    verify(sceneManager, times(1)).requestRenderWithNewSize(900, 1000)
    verify(sceneManager, times(1)).requestRenderWithNewSize(1100, 1200)
    Disposer.dispose(sceneManager)
  }

  @Test
  fun `listener is removed on SceneManager disposal`() = runTest {
    val sceneManager =
      mock<LayoutlibSceneManager>().also {
        whenever(it.sceneRenderConfiguration).thenReturn(layoutlibSceneManagerConfiguration)
        showDecoration = true
      }
    val configuration = createConfiguration(500, 600)
    val listener =
      ConfigurationResizeListener(
          sceneManager,
          configuration,
          StandardTestDispatcher(testScheduler),
        )
        .also { advanceUntilIdle() }

    configuration.addListener(listener)

    Disposer.dispose(sceneManager) // Simulate disposal
    configuration.updateScreenSize(12, 12)
    advanceUntilIdle()
    verify(sceneManager, never()).viewObject
    verify(sceneManager, never()).requestRenderWithNewSize(any(), any())
    Disposer.dispose(sceneManager)
  }

  // Helper function to create a Configuration
  private fun createConfiguration(width: Int, height: Int): Configuration {
    val configuration =
      Configuration.create(configurationSettings, FolderConfiguration.createDefault())
    configuration.setDevice(device(width, height), true)

    return configuration
  }

  private fun device(width: Int, height: Int): Device =
    Device.Builder()
      .apply {
        setTagId("")
        setName("Custom")
        setId(Configuration.CUSTOM_DEVICE_ID)
        setManufacturer("")
        addSoftware(Software())
        addState(
          State().apply {
            name = "default"
            isDefaultState = true
            orientation = ScreenOrientation.PORTRAIT
            hardware =
              Hardware().apply {
                screen =
                  Screen().apply {
                    yDimension = height
                    xDimension = width
                    pixelDensity = Density.MEDIUM
                  }
              }
          }
        )
      }
      .build()
}

/** Test implementation of [ConfigurationSettings]. */
class TestConfigurationSettingsImpl : ConfigurationSettings {

  override var defaultDevice: Device? = null
  override var locale: Locale = Locale.ANY
  override var target: IAndroidTarget? = null
  override var stateVersion: Int = 1
  override lateinit var configModule: ConfigurationModelModule
  override lateinit var resolverCache: ResourceResolverCache
  override var localesInProject: ImmutableList<Locale> = ImmutableList.of()
  override var devices: ImmutableList<Device> = ImmutableList.of()
  override var projectTarget: IAndroidTarget? = null
  override var highestApiTarget: IAndroidTarget? = null
  override var targets: Array<IAndroidTarget> = arrayOf()
  override var recentDevices: List<Device> = listOf()
  override var avdDevices: List<Device> = listOf()

  var selectDeviceCallCount = 0
  var getTargetCallCount = 0
  var createDeviceForAvdCallCount = 0
  var getDeviceByIdCallCount = 0

  var getTargetReturn: IAndroidTarget? = null
  var getDeviceByIdReturn: Device? = null
  var createDeviceForAvdReturn: Device? = null

  override fun selectDevice(device: Device) {
    defaultDevice = device
    selectDeviceCallCount++
  }

  override fun getTarget(minVersion: Int): IAndroidTarget? {
    getTargetCallCount++
    return getTargetReturn
  }

  override fun createDeviceForAvd(avd: AvdInfo): Device? {
    createDeviceForAvdCallCount++
    return createDeviceForAvdReturn
  }

  override fun getDeviceById(id: String): Device? {
    getDeviceByIdCallCount++
    return getDeviceByIdReturn
  }
}

class TestView {
  class LayoutParams {
    @JvmField var width: Int = 0
    @JvmField var height: Int = 0
  }

  var _layoutParams = LayoutParams()

  fun getLayoutParams(): LayoutParams {
    return _layoutParams
  }
}
