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
import com.android.tools.idea.avdmanager.AvdScreenData
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
import org.jetbrains.android.actions.RunAndroidAvdManagerAction
import org.jetbrains.annotations.VisibleForTesting
import javax.swing.Icon
import kotlin.math.roundToInt

private val PIXEL_DEVICE_COMPARATOR = PixelDeviceComparator(VarianceComparator.reversed()).reversed()

internal val DEVICE_ID_TO_TOOLTIPS = mapOf(
  "_device_class_phone" to "This reference device uses the COMPACT width size class, " +
    "which represents 99% of Android phones in portrait orientation.",
  "_device_class_foldable" to "This reference device uses the MEDIUM width size class," +
    " which represents foldables in unfolded portrait orientation," +
    " or 94% of all tablets in portrait orientation.",
  "_device_class_tablet" to "This reference device uses the EXPANDED width size class," +
    " which represents 97% of Android tablets in landscape orientation.",
  "_device_class_desktop" to "This reference device uses the EXPANDED width size class," +
    " which represents 97% of Android desktops in landscape orientation."
)

/**
 * New device menu for layout editor.
 * Because we are going to deprecate [DeviceMenuAction], some of the duplicated codes are not shared between them.
 */
class DeviceMenuAction2(private val renderContext: ConfigurationHolder)
  : DropDownAction("Device for Preview", "Device for Preview", StudioIcons.LayoutEditor.Toolbar.VIRTUAL_DEVICES) {

  override fun actionPerformed(e: AnActionEvent) {
    val button = e.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) as? ActionButton ?: return
    updateActions(e.dataContext)

    val toolbar = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, this)
    JBPopupMenu.showBelow(button, toolbar.component)
    // The items in toolbar.component are filled after JBPopupMenu.showBelow() is called.
    // So we install the tooltips after showing.
    getChildren(null).forEachIndexed { index, action ->
      val deviceId = (action as? DeviceMenuAction.SetDeviceAction)?.device?.id ?: return@forEachIndexed
      DEVICE_ID_TO_TOOLTIPS[deviceId]?.let {
        (toolbar.component.components[index] as? ActionMenuItem)?.let { menuItem -> HelpTooltip().setDescription(it).installOn(menuItem) }
      }
    }
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
    val groupedDevices = getSuitableDevices(renderContext.configuration!!)

    addWindowSizeAndNexusSection(groupedDevices[DeviceGroup.NEXUS_XL]?.plus(groupedDevices[DeviceGroup.NEXUS_TABLET] ?: emptyList()))
    groupedDevices[DeviceGroup.WEAR]?.let { addWearDeviceSection(it) }
    groupedDevices[DeviceGroup.TV]?.let { addTvDeviceSection(it) }
    groupedDevices[DeviceGroup.AUTOMOTIVE]?.let { addAutomotiveDeviceSection(it) }
    addCustomDeviceSection()
    addAvdDeviceSection()
    addGenericDeviceAndNewDefinitionSection()
  }

  private fun addWindowSizeAndNexusSection(nexusDevices:  List<Device>?) {
    val windowDevices = AdditionalDeviceService.getInstance()?.getWindowSizeDevices() ?: return
    add(DeviceCategory("Reference Devices", "Reference Devices", StudioIcons.Avd.DEVICE_MOBILE))
    for (device in windowDevices) {
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetDeviceAction(renderContext, getDeviceLabel(device), { updatePresentation(it) }, device, null, selected))
    }

    if (nexusDevices != null) {
      val group = DefaultActionGroup.createPopupGroup { "Phones and Tablets" }.also { sizeGroup ->
        val template = sizeGroup.templatePresentation
        template.isEnabled = true
        val sortedDevices = nexusDevices.sortedWith(PIXEL_DEVICE_COMPARATOR)
        for (device in sortedDevices) {
          val label = getDeviceLabel(device)
          val selected = device == renderContext.configuration?.device
          sizeGroup.addAction(DeviceMenuAction.SetDeviceAction(renderContext, label, { updatePresentation(it) }, device, null, selected))
        }
      }
      add(group)
    }
    addSeparator()
  }

  private fun addWearDeviceSection(wearDevices: List<Device>) {
    val filteredWearDevices = wearDevices.filter { when(it.id) {
      "wear_square_320", "wear_round_360", "wear_round_chin_320_290" -> true
      else -> false
    } }.toList()

    if (filteredWearDevices.isNotEmpty()) {
      add(DeviceCategory("Wear", "Wear devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_WEAR))
      for (device in filteredWearDevices) {
        val label = getDeviceLabel(device)
        val selected = device == renderContext.configuration?.device
        add(DeviceMenuAction.SetWearDeviceAction(renderContext, label, { updatePresentation(it) }, device, null, selected))
      }
      addSeparator()
    }
  }

  private fun addTvDeviceSection(tvDevices: List<Device>) {
    add(DeviceCategory("TV", "Android TV devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_TV))
    for (device in tvDevices) {
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetDeviceAction(renderContext, getDeviceLabel(device), { updatePresentation(it) }, device, null, selected))
    }
    addSeparator()
  }

  private fun addAutomotiveDeviceSection(automotiveDevices: List<Device>) {
    add(DeviceCategory("Auto", "Android Auto devices", StudioIcons.LayoutEditor.Toolbar.DEVICE_AUTOMOTIVE))
    for (device in automotiveDevices) {
      val selected = device == renderContext.configuration?.device
      add(DeviceMenuAction.SetDeviceAction(renderContext, getDeviceLabel(device), { updatePresentation(it) }, device, null, selected))
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
        add(DeviceMenuAction.SetAvdAction(renderContext, { updatePresentation(it) }, device, avdDisplayName, selected))
      }
      addSeparator()
    }
  }

  private fun addGenericDeviceAndNewDefinitionSection() {
    val groupedDevices = getSuitableDevices(renderContext.configuration!!)
    val devices = groupedDevices.getOrDefault(DeviceGroup.GENERIC, emptyList())
    if (devices.isNotEmpty()) {
      val genericGroup = createPopupGroup { "Generic Devices" }
      for (device in devices) {
        val label: String = getDeviceLabel(device)
        val selected = device == renderContext.configuration?.device
        genericGroup.add(DeviceMenuAction.SetDeviceAction(renderContext, label, { updatePresentation(it) }, device, null, selected))
      }
      add(genericGroup)
    }
    add(AddDeviceDefinitionAction())
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

  private class AddDeviceDefinitionAction: AnAction() {
    init {
      templatePresentation.text = "Add Device Definition"
      templatePresentation.icon = null
    }

    override fun actionPerformed(e: AnActionEvent) {
      ActionManager.getInstance().getAction(RunAndroidAvdManagerAction.ID).actionPerformed(e)
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
