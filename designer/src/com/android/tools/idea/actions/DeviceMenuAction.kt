/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.State
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.actions.componentToRestoreFocusTo
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID
import com.android.tools.idea.avd.showAddDeviceDialog
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.configurations.CanonicalDeviceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.configurations.DEVICE_CLASS_DESKTOP_TOOLTIP
import com.android.tools.idea.configurations.DEVICE_CLASS_FOLDABLE_TOOLTIP
import com.android.tools.idea.configurations.DEVICE_CLASS_PHONE_TOOLTIP
import com.android.tools.idea.configurations.DEVICE_CLASS_TABLET_TOOLTIP
import com.android.tools.idea.configurations.DeviceGroup
import com.android.tools.idea.configurations.ReferenceDevice
import com.android.tools.idea.configurations.ReferenceDeviceType
import com.android.tools.idea.configurations.getCanonicalDevice
import com.android.tools.idea.configurations.getReferenceDevice
import com.android.tools.idea.configurations.getSuitableDevices
import com.android.tools.idea.configurations.isCanonicalDevice
import com.android.tools.idea.configurations.isReferenceDevice
import com.android.tools.idea.configurations.virtualFile
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.text.StringUtil
import icons.StudioIcons
import java.util.function.Consumer
import java.util.logging.Logger
import javax.swing.Icon
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.android.AndroidPluginDisposable

/**
 * A data class to encapsulate the defining characteristics of a reference device for comparison.
 */
private data class ReferenceDeviceMetrics(
  val xDimension: Int,
  val yDimension: Int,
  val density: Density,
  val isRound: Boolean,
  val chinSize: Int,
)

/** Creates a [ReferenceDeviceMetrics] object from a [Device] instance. */
private fun metricsFor(device: Device): ReferenceDeviceMetrics {
  val screen = device.defaultState.hardware.screen
  return ReferenceDeviceMetrics(
    xDimension = screen.xDimension,
    yDimension = screen.yDimension,
    density = screen.pixelDensity,
    isRound = device.isScreenRound,
    chinSize = device.chinSize,
  )
}

/**
 * An extension function to determine if a device is a "reference" type (e.g., a canonical device
 * like "Medium Phone", a device-class reference like "Foldable", or the "Custom" device), as
 * opposed to a user-created AVD or other specific instance with a stable ID (e.g. "Pixel 7").
 *
 * Reference devices must be compared by their characteristics, since they do not have stable IDs.
 */
private fun Device.isReferenceType(): Boolean =
  isCanonicalDevice(this) || isReferenceDevice(this) || id == Configuration.CUSTOM_DEVICE_ID

/**
 * Compares two [Device] instances.
 *
 * The comparison strategy depends on the device type:
 * - **Reference Devices**: For devices that lack a stable ID (e.g., "Medium Phone", "Foldable", or
 *   the "Custom" device), this function compares their physical characteristics (dimensions,
 *   density, etc.).
 * - **All Other Devices**: For devices that have a stable ID (e.g., AVDs, specific hardware like
 *   "Pixel 7"), this function compares them by their `id`.
 */
private fun isSameDevice(d1: Device?, d2: Device?): Boolean {
  // 1. Handle trivial cases: same instance or nulls.
  if (d1 === d2) return true
  if (d1 == null || d2 == null) return false

  // 2. Determine comparison strategy based on device type.
  val d1IsReference = d1.isReferenceType()
  val d2IsReference = d2.isReferenceType()

  return if (d1IsReference && d2IsReference) {
    // Both are reference devices (e.g., "Medium Phone", "Foldable").
    // They don't have stable unique IDs, so we compare by their physical characteristics.
    metricsFor(d1) == metricsFor(d2)
  } else {
    // One or both are not reference devices (e.g., a custom AVD).
    // These devices have stable, unique IDs that we can rely on.
    d1.id == d2.id
  }
}

private val PIXEL_DEVICE_COMPARATOR =
  PixelDeviceComparator(VarianceComparator.reversed()).reversed()

val HAS_BEEN_RESIZED = DataKey.create<Boolean>("has_been_resized")

internal val DEVICE_ID_TO_TOOLTIPS =
  mapOf(
    DEVICE_CLASS_PHONE_ID to DEVICE_CLASS_PHONE_TOOLTIP,
    DEVICE_CLASS_FOLDABLE_ID to DEVICE_CLASS_FOLDABLE_TOOLTIP,
    DEVICE_CLASS_TABLET_ID to DEVICE_CLASS_TABLET_TOOLTIP,
    DEVICE_CLASS_DESKTOP_ID to DEVICE_CLASS_DESKTOP_TOOLTIP,
  )

private val EMPTY_DEVICE_CHANGE_LISTENER = object : DeviceChangeListener {}

/**
 * A dropdown menu action that allows the user to select a device for the preview configuration. The
 * menu is dynamically populated with a list of available devices, including reference devices,
 * custom AVDs, and generic devices.
 *
 * @param deviceChangeListener A listener that is notified when the user selects a new device.
 */
class DeviceMenuAction(
  private val deviceChangeListener: DeviceChangeListener = EMPTY_DEVICE_CHANGE_LISTENER
) :
  DropDownAction(
    "Device for Preview",
    "Device for Preview",
    StudioIcons.LayoutEditor.Toolbar.VIRTUAL_DEVICES,
  ) {

  override fun actionPerformed(e: AnActionEvent) {
    val button =
      (e.presentation.getClientProperty(COMPONENT_KEY) as? ActionButton)
        ?: (e.inputEvent?.component as? ActionButton)
        ?: return
    updateActions(e.dataContext)

    val toolbar = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, this)
    toolbar.setDataContext { e.dataContext }
    val popupMenu = toolbar.component
    JBPopupMenu.showBelow(button, popupMenu)
    // The items in toolbar.component are filled after JBPopupMenu.showBelow() is called.
    // So we install the tooltips after showing.
    getChildren(e.actionManager).forEachIndexed { index, action ->
      val deviceId = (action as? SetDeviceAction)?.device?.id ?: return@forEachIndexed
      DEVICE_ID_TO_TOOLTIPS[deviceId]?.let {
        (popupMenu.components[index] as? ActionMenuItem)?.let { menuItem ->
          HelpTooltip().setDescription(it).installOn(menuItem)
        }
      }
    }
    popupMenu.addPopupMenuListener(
      object : PopupMenuListener {
        override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = Unit

        override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = hideAllTooltips()

        override fun popupMenuCanceled(e: PopupMenuEvent) = hideAllTooltips()

        private fun hideAllTooltips() = popupMenu.components.forEach { HelpTooltip.hide(it) }
      }
    )
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    updatePresentation(e)
    e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  /**
   * Finds a device within the nested map that is considered the same as the targetDevice. This
   * function improves on the previous nested-loop implementation by being more efficient and
   * readable. It uses `flatMap` to create a single, flat list of all available devices, then uses
   * `firstOrNull` to find the first match without unnecessary iteration.
   *
   * @param devicesMap A map where values are collections of devices.
   * @param targetDevice The device to find a match for.
   * @return The matching device from the map, or null if no match is found.
   */
  private fun findMatchingDevice(
    devicesMap: Map<*, Collection<Device>>,
    targetDevice: Device?,
  ): Device? {
    if (targetDevice == null) {
      return null
    }
    // Flatten the map of device lists into a single sequence of devices,
    // then find the first one that matches the target device.
    return devicesMap.values
      .flatMap { it }
      .firstOrNull { deviceInGroup -> isSameDevice(deviceInGroup, targetDevice) }
  }

  private fun updatePresentation(e: AnActionEvent) {
    val presentation = e.presentation
    val configuration = e.getData(CONFIGURATIONS)?.firstOrNull()
    val visible = configuration != null
    if (visible) {
      var device = configuration.cachedDevice
      if (device?.id == Configuration.CUSTOM_DEVICE_ID) {
        val suitableDevices = getSuitableDevicesForMenu(configuration)
        // Attempt to find a real device that matches the characteristics of the custom device.
        // If one is found, we use it. Otherwise, we stick with the custom device.
        device = findMatchingDevice(suitableDevices, device) ?: device
      }
      val label = getDeviceLabel(device, true)
      presentation.setText(label, false)
    }
    if (visible != presentation.isVisible) {
      presentation.isVisible = visible
    }
    presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()
    val configuration = context.getData(CONFIGURATIONS)?.firstOrNull() ?: return true
    val hasBeenResized = context.getData(HAS_BEEN_RESIZED) ?: false
    if (hasBeenResized) {
      add(RevertToOriginalAction(deviceChangeListener))
      addSeparator()
    }
    createDeviceMenuList(configuration)
    return true
  }

  private fun createDeviceMenuList(configuration: Configuration) {
    var selectionMade = false
    val groupedDevices = getSuitableDevicesForMenu(configuration)
    val currentDevice = configuration.device
    selectionMade = addReferenceDeviceSection(groupedDevices, currentDevice, selectionMade)
    selectionMade = addWearDeviceSection(groupedDevices, currentDevice, selectionMade)
    selectionMade = addTvDeviceSection(groupedDevices, currentDevice, selectionMade)
    selectionMade = addAutomotiveDeviceSection(groupedDevices, currentDevice, selectionMade)
    selectionMade = addXrDeviceSection(groupedDevices, currentDevice, selectionMade)
    selectionMade = addCustomDeviceSection(currentDevice, selectionMade)
    selectionMade =
      addAvdDeviceSection(configuration.settings.avdDevices, currentDevice, selectionMade)
    addGenericDeviceAndNewDefinitionSection(groupedDevices, currentDevice, selectionMade)
  }

  /**
   * Adds the reference device section to the device menu.
   *
   * @param groupedDevices The map of device groups to lists of devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addReferenceDeviceSection(
    groupedDevices: Map<DeviceGroup, List<Device>>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    add(DeviceCategory("Reference Devices", "Reference Devices", StudioIcons.Avd.DEVICE_MOBILE))

    for (type in ReferenceDeviceType.entries) {
      val device = getReferenceDevice(groupedDevices, type) ?: continue
      val isMatch = isSameDevice(device, currentDevice)
      var selected = false
      if (isMatch && !newSelectionMade) {
        newSelectionMade = true
        selected = true
      }
      add(
        SetDeviceAction(
          getDeviceLabel(device),
          { updatePresentation(it) },
          deviceChangeListener,
          device,
          null,
          selected,
        )
      )
    }

    // Add canonical small and medium phone devices at the top of menu.
    val phoneDevices =
      listOfNotNull(
        getCanonicalDevice(groupedDevices, CanonicalDeviceType.SMALL_PHONE),
        getCanonicalDevice(groupedDevices, CanonicalDeviceType.MEDIUM_PHONE),
      ) + groupedDevices.getOrDefault(DeviceGroup.NEXUS_XL, emptyList())
    newSelectionMade = addDevicesToPopup("Phones", phoneDevices, currentDevice, newSelectionMade)

    // Add canonical medium tablet device at the top of menu.
    val tabletDevices =
      listOfNotNull(getCanonicalDevice(groupedDevices, CanonicalDeviceType.MEDIUM_TABLET)) +
        groupedDevices.getOrDefault(DeviceGroup.NEXUS_TABLET, emptyList())
    newSelectionMade = addDevicesToPopup("Tablets", tabletDevices, currentDevice, newSelectionMade)

    groupedDevices.get(DeviceGroup.DESKTOP)?.let {
      newSelectionMade = addDevicesToPopup("Desktop", it, currentDevice, newSelectionMade)
    }
    addSeparator()
    return newSelectionMade
  }

  /**
   * Adds the wear device section to the device menu.
   *
   * @param groupedDevices The map of device groups to lists of devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addWearDeviceSection(
    groupedDevices: Map<DeviceGroup, List<Device>>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    val wearDevices = groupedDevices.get(DeviceGroup.WEAR) ?: return newSelectionMade
    add(DeviceCategory("Wear", "Wear devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_WEAR))
    for (device in wearDevices) {
      val label = getDeviceLabel(device)
      val isMatch = isSameDevice(device, currentDevice)
      var selected = false
      if (isMatch && !newSelectionMade) {
        newSelectionMade = true
        selected = true
      }
      add(
        SetWearDeviceAction(
          label,
          { updatePresentation(it) },
          deviceChangeListener,
          device,
          null,
          selected,
        )
      )
    }
    addSeparator()
    return newSelectionMade
  }

  /**
   * Adds the TV device section to the device menu.
   *
   * @param groupedDevices The map of device groups to lists of devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addTvDeviceSection(
    groupedDevices: Map<DeviceGroup, List<Device>>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    val tvDevices = groupedDevices.get(DeviceGroup.TV) ?: return newSelectionMade
    add(DeviceCategory("TV", "Television devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_TV))
    for (device in tvDevices) {
      val isMatch = isSameDevice(device, currentDevice)
      var selected = false
      if (isMatch && !newSelectionMade) {
        newSelectionMade = true
        selected = true
      }
      add(
        SetDeviceAction(
          getDeviceLabel(device),
          { updatePresentation(it) },
          deviceChangeListener,
          device,
          null,
          selected,
        )
      )
    }
    addSeparator()
    return newSelectionMade
  }

  /**
   * Adds the automotive device section to the device menu.
   *
   * @param groupedDevices The map of device groups to lists of devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addAutomotiveDeviceSection(
    groupedDevices: Map<DeviceGroup, List<Device>>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    val automotiveDevices = groupedDevices.get(DeviceGroup.AUTOMOTIVE) ?: return newSelectionMade
    add(
      DeviceCategory(
        "Auto",
        "Android Auto devices",
        StudioIcons.LayoutEditor.Toolbar.DEVICE_AUTOMOTIVE,
      )
    )
    for (device in automotiveDevices) {
      val isMatch = isSameDevice(device, currentDevice)
      var selected = false
      if (isMatch && !newSelectionMade) {
        newSelectionMade = true
        selected = true
      }
      add(
        SetDeviceAction(
          getDeviceLabel(device),
          { updatePresentation(it) },
          deviceChangeListener,
          device,
          null,
          selected,
        )
      )
    }
    addSeparator()
    return newSelectionMade
  }

  /**
   * Adds the XR device section to the device menu.
   *
   * @param groupedDevices The map of device groups to lists of devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addXrDeviceSection(
    groupedDevices: Map<DeviceGroup, List<Device>>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    val xrDevices = groupedDevices.get(DeviceGroup.XR) ?: return newSelectionMade
    add(
      DeviceCategory("XR", "Android XR devices", StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_HEADSET)
    )
    for (device in xrDevices) {
      val isMatch = isSameDevice(device, currentDevice)
      var selected = false
      if (isMatch && !newSelectionMade) {
        newSelectionMade = true
        selected = true
      }
      add(
        SetDeviceAction(
          getDeviceLabel(device),
          { updatePresentation(it) },
          deviceChangeListener,
          device,
          null,
          selected,
        )
      )
    }
    addSeparator()
    return newSelectionMade
  }

  /**
   * Adds the custom device section to the device menu.
   *
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addCustomDeviceSection(currentDevice: Device?, selectionMade: Boolean): Boolean {
    var newSelectionMade = selectionMade
    val isMatch = Configuration.CUSTOM_DEVICE_ID == currentDevice?.id
    var selected = false
    if (isMatch && !newSelectionMade) {
      newSelectionMade = true
      selected = true
    }
    add(SetCustomDeviceAction({ updatePresentation(it) }, currentDevice, selected))
    addSeparator()
    return newSelectionMade
  }

  /**
   * Adds the AVD device section to the device menu.
   *
   * @param avdDevices The list of AVD devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addAvdDeviceSection(
    avdDevices: List<Device>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    if (avdDevices.isNotEmpty()) {
      add(
        DeviceCategory(
          "Virtual Device",
          "Android Virtual Devices",
          StudioIcons.LayoutEditor.Toolbar.VIRTUAL_DEVICES,
        )
      )
      for (device in avdDevices) {
        val isMatch = currentDevice?.id == device.id
        var selected = false
        if (isMatch && !newSelectionMade) {
          newSelectionMade = true
          selected = true
        }
        val avdDisplayName = "AVD: " + device.displayName
        add(
          SetAvdAction(
            { updatePresentation(it) },
            deviceChangeListener,
            device,
            avdDisplayName,
            selected,
          )
        )
      }
      addSeparator()
    }
    return newSelectionMade
  }

  /**
   * Adds the generic device and new definition section to the device menu.
   *
   * @param groupedDevices The map of device groups to lists of devices.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this section.
   */
  private fun addGenericDeviceAndNewDefinitionSection(
    groupedDevices: Map<DeviceGroup, List<Device>>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    val devices = groupedDevices.get(DeviceGroup.GENERIC) ?: return selectionMade
    val newSelectionMade =
      addDevicesToPopup("Generic Devices", devices, currentDevice, selectionMade)
    add(AddDeviceDefinitionAction())
    return newSelectionMade
  }

  /**
   * Adds a group of devices to the popup menu.
   *
   * @param title The title of the group.
   * @param devices The list of devices to add.
   * @param currentDevice The currently selected device.
   * @param selectionMade A boolean indicating whether a selection has already been made.
   * @return A boolean indicating whether a selection was made in this group.
   */
  private fun addDevicesToPopup(
    title: String,
    devices: List<Device>,
    currentDevice: Device?,
    selectionMade: Boolean,
  ): Boolean {
    var newSelectionMade = selectionMade
    val group = DefaultActionGroup(title, true)
    add(group)

    for (device in devices) {
      val label = getDeviceLabel(device)
      val isMatch = isSameDevice(device, currentDevice)
      var selected = false
      if (isMatch && !newSelectionMade) {
        newSelectionMade = true
        selected = true
      }
      group.addAction(
        SetDeviceAction(
          label,
          { updatePresentation(it) },
          deviceChangeListener,
          device,
          null,
          selected,
        )
      )
    }
    return newSelectionMade
  }

  private fun getDeviceLabel(device: Device): String {
    val screen = device.defaultHardware.screen
    val density = screen.pixelDensity
    val xDp = screen.xDimension.toDp(density).roundToInt()
    val yDp = screen.yDimension.toDp(density).roundToInt()
    return "${device.displayName} ($xDp × $yDp dp, ${density.resourceValue})"
  }

  companion object {
    /** Get the non-generic devices in the dropdown menu. */
    fun getSortedMajorDevices(config: Configuration): List<Device> {
      val groupedDevices = getSuitableDevicesForMenu(config)
      return listOf(
          ReferenceDevice.getWindowSizeDevices(),
          groupedDevices[DeviceGroup.NEXUS_XL],
          groupedDevices[DeviceGroup.NEXUS_TABLET],
          groupedDevices[DeviceGroup.DESKTOP],
          groupedDevices[DeviceGroup.WEAR],
          groupedDevices[DeviceGroup.TV],
          groupedDevices[DeviceGroup.AUTOMOTIVE],
          groupedDevices[DeviceGroup.XR],
          config.settings.avdDevices,
          groupedDevices[DeviceGroup.GENERIC],
        )
        .map { it ?: emptyList() }
        .flatten()
    }

    private fun getSuitableDevicesForMenu(config: Configuration): Map<DeviceGroup, List<Device>> {
      return getSuitableDevices(config).mapValues {
        when (it.key) {
          DeviceGroup.NEXUS_XL,
          DeviceGroup.NEXUS_TABLET -> it.value.sortedWith(PIXEL_DEVICE_COMPARATOR)
          DeviceGroup.WEAR ->
            it.value.filter {
              when (it.id) {
                "wearos_large_round",
                "wearos_small_round",
                "wearos_square",
                "wearos_rect" -> true
                else -> false
              }
            }
          else -> it.value
        }
      }
    }
  }
}

private class DeviceCategory(text: String?, description: String?, private val myIcon: Icon?) :
  AnAction(text, description, null) {
  override fun update(e: AnActionEvent) {
    val p = e.presentation
    p.isEnabled = false
    p.disabledIcon = myIcon
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) = Unit
}

class AddDeviceDefinitionAction : AnAction() {
  init {
    templatePresentation.text = "Add Device Definition"
    templatePresentation.icon = null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val config = e.dataContext.getData(CONFIGURATIONS)?.firstOrNull() ?: return
    val project = ConfigurationManager.getFromConfiguration(config).project
    val coroutineScope = AndroidPluginDisposable.getProjectInstance(project).createCoroutineScope()

    coroutineScope.launch(Dispatchers.EDT) {
      val avdInfo = showAddDeviceDialog(project, e.componentToRestoreFocusTo()) ?: return@launch
      val device = config.settings.createDeviceForAvd(avdInfo) ?: return@launch
      config.setDevice(device, true)
    }
  }
}

private const val NEXUS_NAME = "Nexus"
private const val PIXEL_NAME = "Pixel"

private val GENERATION_REGEX = "\\d+".toRegex()

/**
 * Comparator for sorting pixel devices by the generations and variances. First it sorts the
 * generation, such as: Nexus 5, Nexus 6, Pixel, Pixel 2, Pixel 3, ...
 *
 * If the compared devices are in the same generation, then use [varianceComparator] to sort their
 * variances.
 */
private class PixelDeviceComparator(val varianceComparator: Comparator<String>) :
  Comparator<Device> {

  private enum class Series {
    PIXEL,
    NEXUS,
    OTHER,
  }

  private fun getSeries(name: String): Series {
    return when {
      name.startsWith(PIXEL_NAME) -> Series.PIXEL
      name.startsWith(NEXUS_NAME) -> Series.NEXUS
      else -> Series.OTHER
    }
  }

  override fun compare(o1: Device?, o2: Device?): Int {
    when {
      o1 == null && o2 == null -> return 0
      o1 == null && o2 != null -> return -1
      o1 !== null && o2 == null -> return 1
    }

    val name1 = o1!!.displayName
    val name2 = o2!!.displayName

    val series1 = getSeries(name1)
    val series2 = getSeries(name2)

    return when {
      series1 == Series.NEXUS && series2 == Series.NEXUS -> {
        compareGeneration(name1.substringAfter(NEXUS_NAME), name2.substringAfter(NEXUS_NAME))
      }
      series1 == Series.PIXEL && series2 == Series.PIXEL -> {
        compareGeneration(name1.substringAfter(PIXEL_NAME), name2.substringAfter(PIXEL_NAME))
      }
      series1 == Series.NEXUS && series2 == Series.PIXEL -> -1
      series1 == Series.PIXEL && series2 == Series.NEXUS -> 1
      series1 == Series.OTHER && series2 != Series.OTHER -> -1
      series1 != Series.OTHER && series2 == Series.OTHER -> 1
      else -> 0
    }
  }

  private fun compareGeneration(name1: String, name2: String): Int {
    val (gen1, suffix1) = getGenerationAndSuffixPair(name1)
    val (gen2, suffix2) = getGenerationAndSuffixPair(name2)
    return if (gen1 == gen2) varianceComparator.compare(suffix1, suffix2) else gen1 - gen2
  }

  private fun getGenerationAndSuffixPair(str: String): Pair<Int, String> {
    val range = GENERATION_REGEX.find(str)?.range
    return if (range == null) 0 to str
    else {
      str.substring(range).toInt() to str.substring(range.last + 1).trimStart()
    }
  }
}

private val VARIANCE_ORDER = arrayOf("", "XL", "a", "a XL")

/** Sort the strings by the variant suffixes. The order follows [VARIANCE_ORDER]. */
private object VarianceComparator : Comparator<String> {
  override fun compare(o1: String?, o2: String?): Int {
    when {
      o1.isNullOrBlank() && o2.isNullOrBlank() -> return 0
      o1.isNullOrBlank() && !o2.isNullOrBlank() -> return -1
      !o1.isNullOrBlank() && o2.isNullOrBlank() -> return 1
    }

    val order1 = VARIANCE_ORDER.indexOf(o1)
    val order2 = VARIANCE_ORDER.indexOf(o2)

    return when {
      order1 == -1 -> if (order2 == -1) 0 else -1
      order2 == -1 -> 1
      else -> order1 - order2
    }
  }
}

abstract class DeviceAction(
  title: String?,
  private val updatePresentationCallback: Consumer<AnActionEvent>,
  icon: Icon?,
) : ConfigurationAction(title, icon) {
  protected abstract val device: Device?

  override fun updatePresentation(event: AnActionEvent) {
    updatePresentationCallback.accept(event)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

open class SetDeviceAction(
  private val title: String,
  updatePresentationCallback: Consumer<AnActionEvent>,
  protected val deviceChangeListener: DeviceChangeListener,
  public override val device: Device,
  defaultIcon: Icon?,
  private val selected: Boolean,
) : DeviceAction(null, updatePresentationCallback, getBestIcon(title, defaultIcon)) {

  override fun update(event: AnActionEvent) {
    super.update(event)
    val presentation = event.presentation
    Toggleable.setSelected(presentation, selected)

    // The name of AVD device may contain underline character, but they should not be recognized as
    // the mnemonic.
    presentation.setText(title, false)
  }

  override fun updateConfiguration(configuration: Configuration, commit: Boolean) {
    // Attempt to jump to the default orientation of the new device; for example, if you're viewing
    // a layout in
    // portrait orientation on a Nexus 4 (its default), and you switch to a Nexus 10, we jump to
    // landscape orientation
    // (its default) unless of course there is a different layout that is the best fit for that
    // device.
    val prevDevice = configuration.cachedDevice
    val projectState =
      ConfigurationManager.getFromConfiguration(configuration).stateManager.projectState
    val lastSelectedNonWearStateName = projectState.nonWearDeviceLastSelectedStateName
    val newDefaultStateName: String = device.defaultState.name
    val wantedState: State? = lastSelectedNonWearStateName?.let { getMatchingState(device, it) }
    var wantedStateName = wantedState?.name ?: newDefaultStateName
    if (
      wantedStateName != newDefaultStateName &&
        projectState.isNonWearDeviceDefaultStateName &&
        !hasBetterMatchingLayoutFile(configuration, device, newDefaultStateName)
    ) {
      wantedStateName = newDefaultStateName
    }
    if (commit) {
      configuration.settings.selectDevice(device)
    }
    configuration.setDevice(device, true)
    configuration.deviceState = getMatchingState(device, wantedStateName)
    deviceChangeListener.onDeviceChanged(prevDevice, device)
  }

  private fun hasBetterMatchingLayoutFile(
    configuration: Configuration,
    device: Device,
    stateName: String,
  ): Boolean {
    if (configuration.virtualFile == null) {
      return false
    }
    return ConfigurationMatcher.getBetterMatch(configuration, device, stateName, null, null) != null
  }

  private fun getMatchingState(device: Device, stateName: String): State? {
    return device.allStates.firstOrNull { state -> state.name.equals(stateName, ignoreCase = true) }
  }

  companion object {
    private fun getBestIcon(title: String, defaultIcon: Icon?): Icon? {
      return if (isBetterMatchLabel(title)) {
        getBetterMatchIcon()
      } else defaultIcon
    }
  }
}

private class RevertToOriginalAction(private val deviceChangeListener: DeviceChangeListener) :
  AnAction("Original") {
  override fun actionPerformed(e: AnActionEvent) {
    deviceChangeListener.onRevertToOriginal()
  }
}

private class SetWearDeviceAction(
  title: String,
  updatePresentationCallback: Consumer<AnActionEvent>,
  deviceChangeListener: DeviceChangeListener,
  device: Device,
  defaultIcon: Icon?,
  selected: Boolean,
) :
  SetDeviceAction(
    title,
    updatePresentationCallback,
    deviceChangeListener,
    device,
    defaultIcon,
    selected,
  ) {
  override fun updateConfiguration(configuration: Configuration, commit: Boolean) {
    val prevDevice = configuration.cachedDevice
    var newState: String? = null

    // For wear device, we force setup the device state because the orientation must be specific.
    // - The square and round are always portrait.
    // - The chin devices is always landscape.
    if (device.chinSize != 0) {
      // Chin device must be landscape
      val state = device.getState(ScreenOrientation.LANDSCAPE.shortDisplayValue)
      if (state != null) {
        newState = state.name
        configuration.deviceState = state
      } else {
        Logger.getLogger(DeviceMenuAction::class.java.name)
          .warning("A wear chin device must have landscape state")
      }
    } else {
      // Round and Square device must be PORTRAIT
      val state = device.getState(ScreenOrientation.PORTRAIT.shortDisplayValue)
      if (state != null) {
        newState = state.name
        configuration.deviceState = state
      } else {
        Logger.getLogger(DeviceMenuAction::class.java.name)
          .warning("A wear round or square device must have portrait state")
      }
    }
    if (newState != null) {
      configuration.setDeviceStateName(newState)
    }
    if (commit) {
      configuration.settings.selectDevice(device)
    }
    configuration.setDevice(device, true)
    deviceChangeListener.onDeviceChanged(prevDevice, device)
  }
}

private const val CUSTOM_DEVICE_NAME = "Custom"

private class SetCustomDeviceAction(
  updatePresentationCallback: Consumer<AnActionEvent>,
  private val baseDevice: Device?,
  private val selected: Boolean,
) : DeviceAction(CUSTOM_DEVICE_NAME, updatePresentationCallback, null) {
  var customDevice: Device? = null
  override val device: Device?
    get() = customDevice

  override fun update(event: AnActionEvent) {
    super.update(event)
    Toggleable.setSelected(event.presentation, selected)
  }

  override fun updateConfiguration(configuration: Configuration, commit: Boolean) {
    baseDevice?.let {
      val customBuilder = Device.Builder(it)
      customBuilder.setName(CUSTOM_DEVICE_NAME)
      customBuilder.setId(Configuration.CUSTOM_DEVICE_ID)
      customDevice = customBuilder.build()
      configuration.setDevice(customDevice, false)
    }
  }
}

private class SetAvdAction(
  updatePresentationCallback: Consumer<AnActionEvent>,
  private val deviceChangeListener: DeviceChangeListener,
  override val device: Device,
  displayName: String,
  private val selected: Boolean,
) : DeviceAction(displayName, updatePresentationCallback, null) {
  override fun update(event: AnActionEvent) {
    super.update(event)
    Toggleable.setSelected(event.presentation, selected)
  }

  override fun updateConfiguration(configuration: Configuration, commit: Boolean) {
    if (commit) {
      configuration.settings.selectDevice(device)
    }
    // TODO: force set orientation for virtual wear os device
    configuration.setDevice(device, false)
    deviceChangeListener.onDeviceChanged(configuration.cachedDevice, device)
  }
}

/** The callback when device is changed by the [DeviceAction]. */
interface DeviceChangeListener {
  fun onDeviceChanged(oldDevice: Device?, newDevice: Device) {}

  fun onRevertToOriginal() {}
}

/**
 * Returns a suitable label to use to display the given device
 *
 * @param device the device to produce a label for
 * @param brief if true, generate a brief label (suitable for a toolbar button), otherwise a fuller
 *   name (suitable for a menu item)
 * @return the label
 */
fun getDeviceLabel(device: Device?, brief: Boolean): String {
  if (device == null) {
    return ""
  }
  var name = device.displayName
  if (brief) {
    // Produce a really brief summary of the device name, suitable for
    // use in the narrow space available in the toolbar for example
    val nexus = name.indexOf("Nexus") // $NON-NLS-1$
    if (nexus != -1) {
      var begin = name.indexOf('(')
      if (begin != -1) {
        begin++
        val end = name.indexOf(')', begin)
        if (end != -1) {
          return if (name == "Nexus 7 (2012)") {
            "Nexus 7"
          } else {
            name.substring(begin, end).trim { it <= ' ' }
          }
        }
      }
    }
    val skipPrefix = "Android "
    name = StringUtil.trimStart(name, skipPrefix)
  }
  return name
}

/** Convert px to dp. The formula is "px = dp * (dpi / 160)" */
private fun Int.toDp(density: Density): Double = (this.toDouble() * 160 / density.dpiValue)
