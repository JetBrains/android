/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanagerv2

import com.android.adblib.utils.createChildScope
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.CreateDeviceAction
import com.android.sdklib.deviceprovisioner.CreateDeviceTemplateAction
import com.android.sdklib.deviceprovisioner.DeviceAction
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceTemplate
import com.android.sdklib.deviceprovisioner.SetChange
import com.android.sdklib.deviceprovisioner.TemplateState
import com.android.sdklib.deviceprovisioner.pairWithNestedState
import com.android.sdklib.deviceprovisioner.trackSetChanges
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.categorytable.CategoryTable
import com.android.tools.adtui.categorytable.CategoryTablePersistentStateComponent
import com.android.tools.adtui.categorytable.CategoryTableStateSerializer
import com.android.tools.adtui.categorytable.IconButton
import com.android.tools.adtui.categorytable.RowKey.ValueRowKey
import com.android.tools.adtui.categorytable.enumSerializer
import com.android.tools.adtui.categorytable.stringSerializer
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.devicemanagerv2.DeviceTableColumns.columns
import com.android.tools.idea.devicemanagerv2.details.DeviceDetailsPanel
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.tools.idea.wearpairing.WearPairingManager
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ConcurrentHashMultiset
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.AndroidPluginDisposable
import java.awt.BorderLayout
import javax.swing.JPanel

/** The main Device Manager panel, containing a table of devices and a toolbar of buttons above. */
internal class DeviceManagerPanel
@VisibleForTesting
constructor(
  val project: Project?,
  val panelScope: CoroutineScope,
  val uiDispatcher: CoroutineDispatcher,
  private val devices: StateFlow<List<DeviceHandle>>,
  private val templates: StateFlow<List<DeviceTemplate>>,
  createDeviceActions: List<CreateDeviceAction>,
  createTemplateActions: List<CreateDeviceTemplateAction>,
  pairedDevicesFlow: Flow<Map<String, List<PairingStatus>>>,
) : JPanel(), UiDataProvider {

  constructor(
    project: Project,
    deviceProvisioner: DeviceProvisioner =
      project.service<DeviceProvisionerService>().deviceProvisioner,
  ) : this(
    project,
    AndroidCoroutineScope(AndroidPluginDisposable.getProjectInstance(project)),
    uiThread,
    deviceProvisioner.devices,
    deviceProvisioner.templates,
    deviceProvisioner.createDeviceActions(),
    deviceProvisioner.createTemplateActions(),
    WearPairingManager.getInstance().pairedDevicesFlow(),
  )

  constructor(
    parentScope: CoroutineScope,
    deviceProvisioner: DeviceProvisioner,
  ) : this(
    null,
    parentScope.createChildScope(isSupervisor = true),
    uiThread,
    deviceProvisioner.devices,
    deviceProvisioner.templates,
    deviceProvisioner.createDeviceActions(),
    deviceProvisioner.createTemplateActions(),
    WearPairingManager.getInstance().pairedDevicesFlow(),
  )

  private val splitter = JBSplitter(true)
  private val scrollPane =
    JBScrollPane().apply { border = JBUI.Borders.customLineTop(JBColor.border()) }

  /**
   * Depending on how many ways we have to create devices, this is either null, an AnAction that
   * creates a device, or a DropDownAction that shows a popup menu of ways to create a device.
   */
  private val addDevice: AnAction? = run {
    val createActionsCount = createDeviceActions.size + createTemplateActions.size
    val createDeviceActions = createDeviceActions.map { it.toAnAction(createActionsCount == 1) }
    val createTemplateActions = createTemplateActions.map { it.toAnAction(createActionsCount == 1) }
    val createActions = createDeviceActions + createTemplateActions

    when (createActionsCount) {
      0 -> null
      1 -> createActions[0]
      else ->
        DropDownAction("Add Device", "Add a new device", StudioIcons.Common.ADD).also {
          it.addAll(createActions)
        }
    }
  }

  private val emptyStatePanel =
    EmptyStatePanel(
        "No devices connected.",
        actionData =
          addDevice?.let {
            ActionData(it.templatePresentation.description.titlecase() + "...") {
              ActionToolbarUtil.findActionButton(toolbar, addDevice)?.click()
            }
          },
      )
      .apply { background = JBUI.CurrentTheme.Table.background(false, true) }

  internal var deviceTable =
    CategoryTable(
      columns(project, panelScope),
      DeviceRowData::key,
      uiDispatcher,
      rowDataProvider = ::provideRowData,
      emptyStatePanel = emptyStatePanel,
    )

  private val templateInstantiationCount = ConcurrentHashMultiset.create<DeviceTemplate>()

  private val pairedDevicesFlow =
    pairedDevicesFlow.stateIn(panelScope, SharingStarted.Lazily, emptyMap())

  private val toolbar: ActionToolbar = run {
    val groupingActions =
      DefaultActionGroup("Group", null, StudioIcons.Common.GROUP).apply {
        isPopup = true
        add(Separator.create("Group By"))
        add(GroupByNoneAction(deviceTable))
        add(GroupingAction(deviceTable, DeviceTableColumns.FormFactor, "Form Factor"))
        add(GroupingAction(deviceTable, DeviceTableColumns.Status))
        add(GroupingAction(deviceTable, DeviceTableColumns.HandleType))
      }

    val typeSpecificActions =
      ActionManager.getInstance().getAction("Android.DeviceManager.TypeSpecificActions")

    createToolbar(
      listOfNotNull(groupingActions, Separator.create(), addDevice, typeSpecificActions)
    )
  }

  init {
    layout = BorderLayout()

    add(toolbar.component, BorderLayout.NORTH)

    deviceTable.categoryIndent = 0

    val persistentState = project?.service<DeviceTablePersistentStateComponent>()
    if (persistentState != null) {
      persistentState.table = deviceTable
    } else {
      deviceTable.toggleSortOrder(DeviceTableColumns.nameAttribute)
    }
    deviceTable.addToScrollPane(scrollPane)

    splitter.firstComponent = scrollPane
    // second component will be the details panel if/when it's created
    add(splitter, BorderLayout.CENTER)

    panelScope.launch(uiDispatcher) { trackDevices() }
    panelScope.launch(uiDispatcher) { trackDeviceTemplates() }

    // Keep the device details synced with the selected row.
    panelScope.launch(uiDispatcher) {
      deviceTable.selection.asFlow().collect { selectedRows ->
        if (deviceDetailsPanelRow != null) {
          (selectedRows.singleOrNull() as? ValueRowKey)?.let { selectedRow ->
            deviceTable.values.find { it.key() == selectedRow.key }?.let { showDeviceDetails(it) }
          }
        }
      }
    }
  }

  private suspend fun trackDevices() {
    devices
      .map { it.toSet() }
      .trackSetChanges()
      .collect { change ->
        when (change) {
          is SetChange.Add -> trackDevice(change.value)
          is SetChange.Remove -> {} // we use device scope ending to detect removal
        }
      }
  }

  private suspend fun trackDeviceTemplates() {
    val currentTemplates = mutableMapOf<DeviceTemplate, TemplateState>()
    templates.pairWithNestedState(DeviceTemplate::stateFlow).collect { pairs ->
      val newTemplates = pairs.map { it.first }.toSet()
      val removed = currentTemplates.keys - newTemplates
      removed.forEach {
        currentTemplates.remove(it)
        deviceTable.removeRowByKey(it)
      }
      for ((template, state) in pairs) {
        if (currentTemplates[template] != state) {
          currentTemplates[template] = state
          deviceTable.addOrUpdateRow(DeviceRowData.create(template))
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun trackDevice(handle: DeviceHandle) {
    panelScope.launch {
      // As long as the device scope is active, update its state in the table.
      // When it completes, remove it from the table.
      handle.scope
        .launch {
          // When the device's state changes, or its paired devices change, update the row.
          // It would be more natural to use combine() here, but we need to get the wearPairingId
          // from state.
          handle.stateFlow
            .pairWithConnectionState()
            .flatMapLatest { (state, _) ->
              pairedDevicesFlow
                .map { allPairedDevices ->
                  allPairedDevices[state.properties.wearPairingId] ?: emptyList()
                }
                .distinctUntilChanged()
                .map { pairedDevices -> DeviceRowData.create(handle, pairedDevices) }
            }
            .collect {
              withContext(uiDispatcher) {
                if (deviceTable.addOrUpdateRow(it, beforeKey = handle.sourceTemplate)) {
                  handle.sourceTemplate?.let {
                    if (templateInstantiationCount.add(it, 1) == 0) {
                      if (deviceTable.selection.selectedKeys().contains(ValueRowKey(it))) {
                        deviceTable.selection.selectRow(ValueRowKey(handle))
                      }
                      deviceTable.setRowVisibleByKey(it, false)
                    }
                  }
                }
              }
            }
        }
        .join()

      withContext(uiDispatcher) {
        deviceTable.removeRowByKey(handle)
        handle.sourceTemplate?.let {
          if (templateInstantiationCount.remove(it, 1) == 1) {
            deviceTable.setRowVisibleByKey(it, true)
          }
        }
      }
    }
  }

  private fun <A : DeviceAction> A.toAnAction(
    action: suspend A.() -> Unit,
    isIconEnabled: Boolean = true,
  ): DumbAwareAction {
    panelScope.launch {
      // Any time the DeviceAction presentation changes, update the ActivityTracker so that we can
      // update the AnAction presentation
      presentation.collect { ActivityTracker.getInstance().inc() }
    }
    val icon = if (isIconEnabled) presentation.value.icon else null
    return object : DumbAwareAction(presentation.value.label, presentation.value.label, icon) {
      override fun getActionUpdateThread() = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = presentation.value.enabled
        if (isIconEnabled) {
          e.presentation.icon = presentation.value.icon
        }
        e.presentation.text = presentation.value.label
        e.presentation.description = presentation.value.label
      }

      override fun actionPerformed(e: AnActionEvent) {
        panelScope.launch { action() }
      }
    }
  }

  private fun CreateDeviceAction.toAnAction(isIconEnabled: Boolean) =
    toAnAction(CreateDeviceAction::create, isIconEnabled)

  private fun CreateDeviceTemplateAction.toAnAction(isIconEnabled: Boolean) =
    toAnAction(CreateDeviceTemplateAction::create, isIconEnabled)

  private fun createToolbar(actions: List<AnAction>): ActionToolbar {
    val toolbar =
      ActionManager.getInstance().createActionToolbar(TOOLBAR_ID, DefaultActionGroup(actions), true)
    toolbar.layoutStrategy = ToolbarLayoutStrategy.AUTOLAYOUT_STRATEGY
    toolbar.setLayoutSecondaryActions(true)
    toolbar.targetComponent = this
    ActionToolbarUtil.makeToolbarNavigable(toolbar)
    return toolbar
  }

  internal var deviceDetailsPanelRow: DeviceRowData? = null
    set(row) {
      if (field != row) {
        field = row
        deviceDetailsPanel = row?.let { createDetailsPanel(it) }
      }
    }

  internal var deviceDetailsPanel: DeviceDetailsPanel?
    get() = splitter.secondComponent as? DeviceDetailsPanel
    set(panel) {
      deviceDetailsPanel?.dispose()
      splitter.secondComponent = panel
    }

  fun showDeviceDetails(row: DeviceRowData) {
    getOrCreateDetailsPanelForDevice(row).showDeviceInfo()
  }

  fun showPairedDevices(row: DeviceRowData) {
    getOrCreateDetailsPanelForDevice(row).showPairedDevices()
  }

  private fun getOrCreateDetailsPanelForDevice(row: DeviceRowData): DeviceDetailsPanel {
    deviceDetailsPanelRow = row
    return deviceDetailsPanel!!
  }

  private fun createDetailsPanel(row: DeviceRowData): DeviceDetailsPanel =
    when (row.handle) {
      null ->
        DeviceDetailsPanel.create(panelScope.createChildScope(isSupervisor = true), row.template!!)
      else ->
        DeviceDetailsPanel.create(
          project,
          panelScope.createChildScope(isSupervisor = true),
          row.handle,
          devices,
          pairedDevicesFlow,
        )
    }.apply { addCloseActionListener { deviceDetailsPanelRow = null } }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DEVICE_MANAGER_PANEL_KEY] = this
    sink[DEVICE_MANAGER_COROUTINE_SCOPE_KEY] = panelScope
  }
}

@UiThread
internal suspend fun IconButton.trackActionPresentation(action: DeviceAction?) =
  when (action) {
    null -> isEnabled = false
    else ->
      action.presentation.collect {
        isEnabled = it.enabled
        baseIcon = it.icon
        toolTipText = if (it.enabled) it.label else it.detail
      }
  }

/**
 * Joins the DeviceState with the nested adblib DeviceState, producing a Flow that updates when
 * either DeviceState changes.
 */
private fun Flow<DeviceState>.pairWithConnectionState():
  Flow<Pair<DeviceState, com.android.adblib.DeviceState?>> = flatMapLatest { state ->
  state.connectedDevice?.deviceInfoFlow?.map { Pair(state, it.deviceState) }
    ?: flowOf(Pair(state, null))
}

private const val TOOLBAR_ID = "DeviceManager2"

internal val DEVICE_MANAGER_PANEL_KEY = DataKey.create<DeviceManagerPanel>("DeviceManagerPanel")
internal val DEVICE_MANAGER_COROUTINE_SCOPE_KEY =
  DataKey.create<CoroutineScope>("DeviceManagerCoroutineScope")

@Service(Service.Level.PROJECT)
@State(
  name = "DeviceTable",
  storages = [Storage("deviceManager.xml", roamingType = RoamingType.DISABLED)],
)
class DeviceTablePersistentStateComponent : CategoryTablePersistentStateComponent() {
  override val serializer =
    CategoryTableStateSerializer(
      listOf(
        DeviceTableColumns.Status.attribute.enumSerializer("Status"),
        DeviceTableColumns.Name.attribute.stringSerializer("Name"),
        DeviceTableColumns.Api.attribute.stringSerializer("API"),
        DeviceTableColumns.HandleType.attribute.stringSerializer("Type"),
        DeviceTableColumns.FormFactor.enumSerializer("FormFactor"),
      )
    )
}
