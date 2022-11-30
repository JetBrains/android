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
package com.android.tools.idea.configurations

import com.android.ide.common.rendering.HardwareConfigHelper
import com.android.sdklib.devices.Device
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.avdmanager.AvdOptionsModel
import com.android.tools.idea.avdmanager.AvdScreenData
import com.android.tools.idea.avdmanager.AvdWizardUtils
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import icons.StudioIcons
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.Icon
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.math.roundToInt

private val PIXEL_DEVICE_COMPARATOR = PixelDeviceComparator(VarianceComparator.reversed()).reversed()

internal val DEVICE_ID_TO_TOOLTIPS = mapOf(
  "_device_class_phone" to DEVICE_CLASS_PHONE_TOOLTIP,
  "_device_class_foldable" to DEVICE_CLASS_FOLDABLE_TOOLTIP,
  "_device_class_tablet" to DEVICE_CLASS_TABLET_TOOLTIP,
  "_device_class_desktop" to DEVICE_CLASS_DESKTOP_TOOLTIP
)

/**
 * New device menu for layout editor.
 * Because we are going to deprecate [DeviceMenuAction], some of the duplicated codes are not shared between them.
 */
class DeviceMenuAction2(private val renderContext: ConfigurationHolder,
                        private val deviceChangeListener: DeviceMenuAction.DeviceChangeListener)
  : DropDownAction("Device for Preview", "Device for Preview", StudioIcons.LayoutEditor.Toolbar.VIRTUAL_DEVICES) {

  override fun actionPerformed(e: AnActionEvent) {
    val button = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) as? ActionButton ?: return
    updateActions(e.dataContext)

    val toolbar = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, this)
    val popupMenu = toolbar.component
    JBPopupMenu.showBelow(button, popupMenu)
    // The items in toolbar.component are filled after JBPopupMenu.showBelow() is called.
    // So we install the tooltips after showing.
    getChildren(null).forEachIndexed { index, action ->
      val deviceId = (action as? DeviceMenuAction.SetDeviceAction)?.device?.id ?: return@forEachIndexed
      DEVICE_ID_TO_TOOLTIPS[deviceId]?.let {
        (popupMenu.components[index] as? ActionMenuItem)?.let { menuItem -> HelpTooltip().setDescription(it).installOn(menuItem) }
      }
    }
    popupMenu.addPopupMenuListener(object : PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) = Unit

      override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent) = hideAllTooltips()

      override fun popupMenuCanceled(e: PopupMenuEvent) = hideAllTooltips()

      private fun hideAllTooltips() = popupMenu.components.forEach { HelpTooltip.hide(it) }
    })
  }

  override fun displayTextInToolbar(): Boolean = true

  override fun update(e: AnActionEvent) {
    super.update(e)
    updatePresentation(e.presentation)
  }

  private fun updatePresentation(presentation: Presentation) {
    val configuration = renderContext.configuration
    val visible = configuration != null
    if (visible) {
      val device = configuration!!.cachedDevice
      val label = DeviceMenuAction.getDeviceLabel(device, true)
      presentation.setText(label, false)
    }
    if (visible != presentation.isVisible) {
      presentation.isVisible = visible
    }
  }

  @VisibleForTesting
  public override fun updateActions(context: DataContext): Boolean {
    removeAll()
    createDeviceMenuList()
    return true
  }

  private fun createDeviceMenuList() {
    val groupedDevices = getSuitableDevicesForMenu(renderContext.configuration!!)
    addWindowSizeAndNexusSection(groupedDevices)
    addWearDeviceSection(groupedDevices)
    addTvDeviceSection(groupedDevices)
    addAutomotiveDeviceSection(groupedDevices)
    addCustomDeviceSection()
    addAvdDeviceSection()
    addGenericDeviceAndNewDefinitionSection(groupedDevices)
  }

  private fun addWindowSizeAndNexusSection(groupedDevices: Map<DeviceGroup, List<Device>>) {
    val windowDevices = AdditionalDeviceService.getInstance()?.getWindowSizeDevices() ?: return
    add(DeviceCategory("Reference Devices", "Reference Devices", StudioIcons.Avd.DEVICE_MOBILE))
    for (device in windowDevices) {
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetDeviceAction(renderContext,
                                           getDeviceLabel(device),
                                           { updatePresentation(it) },
                                           deviceChangeListener,
                                           device,
                                           null, selected))
    }

    groupedDevices.get(DeviceGroup.NEXUS_XL)?.let { addDevicesToPopup("Phones", it) }
    groupedDevices.get(DeviceGroup.NEXUS_TABLET)?.let { addDevicesToPopup("Tablets", it) }
    groupedDevices.get(DeviceGroup.DESKTOP)?.let { addDevicesToPopup("Desktop", it) }

    addSeparator()
  }

  private fun addWearDeviceSection(groupedDevices: Map<DeviceGroup, List<Device>>) {
    val wearDevices = groupedDevices.get(DeviceGroup.WEAR) ?: return
    add(DeviceCategory("Wear", "Wear devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_WEAR))
    for (device in wearDevices) {
      val label = getDeviceLabel(device)
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetWearDeviceAction(renderContext,
                                               label,
                                               { updatePresentation(it) },
                                               deviceChangeListener,
                                               device,
                                               null,
                                               selected))
    }
    addSeparator()
  }

  private fun addTvDeviceSection(groupedDevices: Map<DeviceGroup, List<Device>>) {
    val tvDevices = groupedDevices.get(DeviceGroup.TV) ?: return
    add(DeviceCategory("TV", "Android TV devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_TV))
    for (device in tvDevices) {
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetDeviceAction(renderContext,
                                           getDeviceLabel(device),
                                           { updatePresentation(it) },
                                           deviceChangeListener,
                                           device,
                                           null, selected))
    }
    addSeparator()
  }

  private fun addAutomotiveDeviceSection(groupedDevices: Map<DeviceGroup, List<Device>>) {
    val automotiveDevices = groupedDevices.get(DeviceGroup.AUTOMOTIVE) ?: return
    add(DeviceCategory("Auto", "Android Auto devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_AUTOMOTIVE))
    for (device in automotiveDevices) {
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetDeviceAction(renderContext,
                                           getDeviceLabel(device),
                                           { updatePresentation(it) },
                                           deviceChangeListener,
                                           device,
                                           null,
                                           selected))
    }
    addSeparator()
  }

  private fun addCustomDeviceSection() {
    add(DeviceMenuAction.SetCustomDeviceAction(renderContext, { updatePresentation(it) }, renderContext.configuration?.device))
    addSeparator()
  }

  private fun addAvdDeviceSection() {
    val devices = getAvdDevices(renderContext.configuration!!)
    if (devices.isNotEmpty()) {
      add(DeviceCategory("Virtual Device", "Android Virtual Devices", StudioIcons.LayoutEditor.Toolbar.VIRTUAL_DEVICES))
      val current = renderContext.configuration?.device
      for (device in devices) {
        val selected = current != null && current.id == device.id
        val avdDisplayName = "AVD: " + device.displayName
        add(DeviceMenuAction.SetAvdAction(renderContext,
                                          { updatePresentation(it) },
                                          deviceChangeListener,
                                          device,
                                          avdDisplayName,
                                          selected))
      }
      addSeparator()
    }
  }

  private fun addGenericDeviceAndNewDefinitionSection(groupedDevices: Map<DeviceGroup, List<Device>>) {
    val devices = groupedDevices.get(DeviceGroup.GENERIC) ?: return
    addDevicesToPopup("Generic Devices", devices)
    add(AddDeviceDefinitionAction(renderContext))
  }

  private fun addDevicesToPopup(title: String, devices: List<Device>) {
    val group = createSubMenuGroup { title }
    add(group)

    val template = group.templatePresentation
    template.isEnabled = true
    for (device in devices) {
      val label = getDeviceLabel(device)
      val selected = device == renderContext.configuration?.device
      group.addAction(DeviceMenuAction.SetDeviceAction(renderContext,
                                                       label,
                                                       { updatePresentation(it) },
                                                       deviceChangeListener,
                                                       device,
                                                       null, selected))
    }
  }

  private fun getDeviceLabel(device: Device): String {
    val screen = device.defaultHardware.screen
    val density = screen.pixelDensity
    val xDp = screen.xDimension.toDp(density).roundToInt()
    val yDp = screen.yDimension.toDp(density).roundToInt()
    val isTv = HardwareConfigHelper.isTv(device)
    val displayedDensity = AvdScreenData.getScreenDensity(device.id, isTv, density.dpiValue.toDouble(), screen.yDimension)
    return "${device.displayName} ($xDp × $yDp dp, ${displayedDensity.resourceValue})"
  }

  companion object {
    /**
     * Get the non-generic devices in the dropdown menu.
     */
    fun getSortedMajorDevices(config: Configuration): List<Device> {
      val groupedDevices = getSuitableDevicesForMenu(config)
      return listOf(
        AdditionalDeviceService.getInstance()?.getWindowSizeDevices(),
        groupedDevices.get(DeviceGroup.NEXUS_XL),
        groupedDevices.get(DeviceGroup.NEXUS_TABLET),
        groupedDevices.get(DeviceGroup.DESKTOP),
        groupedDevices.get(DeviceGroup.WEAR),
        groupedDevices.get(DeviceGroup.TV),
        groupedDevices.get(DeviceGroup.AUTOMOTIVE),
        getAvdDevices(config),
        groupedDevices.get(DeviceGroup.GENERIC),
      ).map { it ?: emptyList() }.flatten()
    }

    private fun getSuitableDevicesForMenu(config: Configuration): Map<DeviceGroup, List<Device>> {
      return getSuitableDevices(config).mapValues {
        when (it.key) {
          DeviceGroup.NEXUS_XL, DeviceGroup.NEXUS_TABLET -> it.value.sortedWith(PIXEL_DEVICE_COMPARATOR)
          DeviceGroup.WEAR -> it.value.filter {
            when (it.id) {
              "wearos_large_round", "wearos_small_round", "wearos_square", "wearos_rect" -> true
              else -> false
            }
          }
          else -> it.value
        }
      }
    }
  }
}

private class DeviceCategory(text: String?, description: String?, private val myIcon: Icon?) : AnAction(text, description, null) {
  override fun update(e: AnActionEvent) {
    val p = e.presentation
    p.isEnabled = false
    p.disabledIcon = myIcon
  }

  override fun actionPerformed(e: AnActionEvent) = Unit
}

class AddDeviceDefinitionAction(private val configurationHolder: ConfigurationHolder): AnAction() {
  init {
    templatePresentation.text = "Add Device Definition"
    templatePresentation.icon = null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val config = configurationHolder.configuration ?: return
    val module = config.module ?: return
    val project = module.project

    val optionsModel = AvdOptionsModel(null)
    val dialog = AvdWizardUtils.createAvdWizard(null, project, optionsModel)

    if (dialog.showAndGet()) {
      // Select the new created AVD device.
      val avdDevice = config.configurationManager.createDeviceForAvd(optionsModel.createdAvd)
      config.setDevice(avdDevice, true)
    }
  }
}

private const val NEXUS_NAME = "Nexus"
private const val PIXEL_NAME = "Pixel"

private val GENERATION_REGEX = "\\d+".toRegex()

/**
 * Comparator for sorting pixel devices by the generations and variances.
 * First it sorts the generation, such as: Nexus 5, Nexus 6, Pixel, Pixel 2, Pixel 3, ...
 *
 * If the compared devices are in the same generation, then use [varianceComparator] to sort their variances.
 */
private class PixelDeviceComparator(val varianceComparator: Comparator<String>): Comparator<Device> {

  private enum class Series {
    PIXEL, NEXUS, OTHER;
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
    return if (range == null) 0 to str else {
      str.substring(range).toInt() to str.substring(range.last + 1).trimStart()
    }
  }
}

private val VARIANCE_ORDER = arrayOf("", "XL", "a", "a XL")

/**
 * Sort the strings by the variant suffixes. The order follows [VARIANCE_ORDER].
 */
private object VarianceComparator: Comparator<String> {
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
