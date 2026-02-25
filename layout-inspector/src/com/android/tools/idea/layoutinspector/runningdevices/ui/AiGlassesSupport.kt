/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices.ui

import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.resource.data.Display
import com.android.tools.idea.layoutinspector.runningdevices.RunningDevicesStateObserver
import com.android.tools.idea.streaming.DEVICE_TYPE_KEY
import com.android.tools.idea.streaming.core.DeviceId
import com.android.tools.idea.streaming.core.DisplayView
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.EventQueue.invokeLater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Maps a [DisplayView] to the id of an app display. This is necessary since while inspecting a phone, the phone app can be connected to a
 * pair of glasses. The glasses would be a separate tab in running devices. Therefore, we need to render across two tabs and map the glasses
 * tab to the glasses display from the app.
 */
data class AiGlassesDisplayPair(val displayView: DisplayView, val appDisplayId: Int)

/** State of the AI Glasses support. */
sealed class AiGlassesState {
  /** We have a glasses RD tab and a secondary display on the device. */
  data class Active(val pair: AiGlassesDisplayPair) : AiGlassesState()

  /** Currently don't have any mapping between glasses RD tab and phone secondary display. Either one or both are missing. */
  object Inactive : AiGlassesState()

  /** An unsupported configuration was encountered. */
  sealed class Error : AiGlassesState() {
    /** Multiple glasses tabs were detected. We don't support this configuration since we don't know which tab is paired with the device. */
    object MultipleGlassesTabs : Error()

    /**
     * Multiple secondary displays were detected in the app. We don't support this configuration since we don't know which one of the
     * secondary displays is used to render the glasses.
     */
    object MultipleSecondaryDisplays : Error()
  }
}

/** A flow that emits the [AiGlassesState] for the visible ai glasses tabs. The flow is idle until an ai glasses tab is visible. */
@OptIn(ExperimentalCoroutinesApi::class)
fun aiGlassesDataFlow(project: Project, model: InspectorModel): Flow<AiGlassesState> {
  val visibleAiGlassesTabsFlow = visibleAiGlassesTabComponentsFlow(project)
  val visibleAiGlassesMainDisplayFlow = visibleAiGlassesTabsFlow.mapToMainDisplayState()

  return visibleAiGlassesMainDisplayFlow
    .flatMapLatest { mainDisplayState ->
      when (mainDisplayState) {
        is GlassesTabsSupportState.Inactive -> flowOf(AiGlassesState.Inactive)
        is GlassesTabsSupportState.Error.MultipleTabs -> flowOf(AiGlassesState.Error.MultipleGlassesTabs)
        is GlassesTabsSupportState.Active -> {
          // We only subscribe to secondary display modifications if there is an active ai glasses tab
          appSecondaryDisplayIdsFlow(model).map { appSecondaryDisplayIds ->
            if (appSecondaryDisplayIds.size > 1) {
              AiGlassesState.Error.MultipleSecondaryDisplays
            } else {
              val appGlassesDisplayId = appSecondaryDisplayIds.firstOrNull()
              if (appGlassesDisplayId != null) {
                // Map the only available secondary display from the app with the only available ai glasses tab in running devices
                AiGlassesState.Active(AiGlassesDisplayPair(mainDisplayState.displayView, appGlassesDisplayId))
              } else {
                AiGlassesState.Inactive
              }
            }
          }
        }
      }
    }
    .distinctUntilChanged()
}

/** A flow that emits the secondary displays detected by the app, each time they change */
private fun appSecondaryDisplayIdsFlow(model: InspectorModel): Flow<List<Int>> {
  return callbackFlow {
      // Use display id instead of full Display object to avoid emitting again on configuration changes
      val getSecondaryDisplayIds = { model.resourceLookup.displays.filter { it.id != Display.MAIN_DISPLAY_ID }.map { it.id } }
      val listener =
        InspectorModel.ModificationListener { _, _, _ ->
          val virtualDisplayIds = getSecondaryDisplayIds()
          this@callbackFlow.trySend(virtualDisplayIds)
        }

      model.addModificationListener(listener)
      this@callbackFlow.trySend(getSecondaryDisplayIds())
      awaitClose { model.removeModificationListener(listener) }
    }
    .distinctUntilChanged()
}

/**
 * Information about Layout Inspector support of ai glasses tabs in Running Devices. The purpose of this is to define a [DisplayView] that
 * Layout Inspector can render on-top.
 */
private sealed class GlassesTabsSupportState {
  /** Layout Inspector glasses view hierarchy can be rendered on-top of [displayView]. */
  data class Active(val displayView: DisplayView) : GlassesTabsSupportState()

  /** No [DisplayView] found to render Layout Inspector glasses view on-top. */
  object Inactive : GlassesTabsSupportState()

  /** An unsupported configuration was encountered. */
  sealed class Error : GlassesTabsSupportState() {
    /** Multiple glasses tabs are present, we don't know which one to render on-top. */
    object MultipleTabs : Error()
  }
}

/** A flow that emits the [GlassesTabsSupportState]. Tries to find a glasses [DisplayView] that Layout Inspector can render on-top. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<List<TabComponents>>.mapToMainDisplayState(): Flow<GlassesTabsSupportState> {
  return flatMapLatest { aiGlassesTabs ->
    if (aiGlassesTabs.isEmpty()) {
      flowOf(GlassesTabsSupportState.Inactive)
    } else if (aiGlassesTabs.size > 1) {
      flowOf(GlassesTabsSupportState.Error.MultipleTabs)
    } else {
      val firstTab = aiGlassesTabs.first()
      firstTab.displayList
        .map { list ->
          val displayView = list.firstOrNull { it.displayId == Display.MAIN_DISPLAY_ID }
          if (displayView != null) GlassesTabsSupportState.Active(displayView) else GlassesTabsSupportState.Inactive
        }
        .distinctUntilChanged()
    }
  }
}

/**
 * A flow that emits the DeviceId for each visible ai glasses tab, each time that the set of visible ai glasses tabs changes. The flow does
 * not emit when any of the other tabs change.
 */
private fun visibleAiGlassesDeviceIdsFlow(project: Project): Flow<List<DeviceId>> {
  return callbackFlow {
      val observer = RunningDevicesStateObserver.getInstance(project)

      val listener =
        object : RunningDevicesStateObserver.Listener {
          override fun onVisibleTabsChanged(visibleTabs: List<DeviceId>) {
            val aiGlassesDeviceIds = mutableListOf<DeviceId>()
            visibleTabs.forEach { deviceId ->
              val content = observer.getTabContent(deviceId) ?: return@forEach
              val dataProvider = DataManager.getInstance().customizeDataContext(DataContext.EMPTY_CONTEXT, content.component)
              val deviceType = DEVICE_TYPE_KEY.getData(dataProvider)
              if (deviceType == DeviceType.AI_GLASSES) {
                aiGlassesDeviceIds.add(deviceId)
              }
            }
            this@callbackFlow.trySend(aiGlassesDeviceIds)
          }

          override fun onExistingTabsChanged(existingTabs: List<DeviceId>) {}
        }
      observer.addListener(listener)
      awaitClose { invokeLater { observer.removeListener(listener) } }
    }
    // Avoid emitting again just because a tab changed, but the glasses tabs haven't changed
    .distinctUntilChanged()
}

/** A flow that emits the [TabComponents] for each visible ai glasses tab, each time that the set of visible ai glasses tabs changes */
private fun visibleAiGlassesTabComponentsFlow(project: Project): Flow<List<TabComponents>> = flow {
  var currentGlassesTabs = mapOf<DeviceId, TabComponents>()
  try {
    visibleAiGlassesDeviceIdsFlow(project).collect { aiGlassesDeviceIds ->
      val newGlassesTabs = mutableMapOf<DeviceId, TabComponents>()
      aiGlassesDeviceIds.forEach { deviceId ->
        val existing = currentGlassesTabs[deviceId]
        if (existing != null) {
          // We don't want to create a new TabComponents for the same tab, each time the visible tabs change.
          // Each time we'd have to dispose the old TabComponents and immediately re-create it.
          newGlassesTabs[deviceId] = existing
        } else {
          // Create the TabComponents only if the tab is for ai glasses.
          val tabComponents = createTabComponents(project, deviceId)
          newGlassesTabs[deviceId] = tabComponents
        }
      }

      // Dispose removed tabs
      val removedTabs = currentGlassesTabs.keys - newGlassesTabs.keys
      removedTabs.forEach { deviceId -> currentGlassesTabs[deviceId]?.let { Disposer.dispose(it) } }

      currentGlassesTabs = newGlassesTabs
      emit(newGlassesTabs.values.toList())
    }
  } finally {
    currentGlassesTabs.values.forEach { Disposer.dispose(it) }
  }
}
