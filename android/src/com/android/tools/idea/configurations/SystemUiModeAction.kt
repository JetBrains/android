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
package com.android.tools.idea.configurations

import com.android.resources.NightMode
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.selectionBackground
import com.intellij.ide.ui.laf.darcula.ui.DarculaMenuSeparatorUI
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RoundedIcon
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.LafIconLookup.getIcon
import com.intellij.util.ui.LafIconLookup.getSelectedIcon
import icons.StudioIcons
import org.jetbrains.annotations.TestOnly
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.plaf.basic.BasicMenuItemUI

private const val POPUP_VERTICAL_BORDER = 6
private const val TITLE_VERTICAL_BORDER = 2
private const val WALLPAPER_ICON_SEPARATION = 2
private const val WALLPAPER_ICON_PADDING = 2
private const val ICON_SIZE = 24
private val sideBorderWidth = JBValue.UIInteger("PopupMenuSeparator.withToEdge", 1)
private val separatorUI = object : DarculaMenuSeparatorUI(){
  override fun getPreferredSize(c: JComponent?): Dimension {
    return Dimension(0, JBValue.UIInteger("PopupMenuSeparator.height", 3).get())
  }

  override fun getStripeIndent(): Int {
    return JBValue.UIInteger("PopupMenuSeparator.stripeIndent", 1).get()
  }

  override fun getStripeWidth(): Int {
    return JBValue.UIInteger("PopupMenuSeparator.stripeWidth", 1).get()
  }

  override fun getWithToEdge(): Int {
    return sideBorderWidth.get()
  }
}

class SystemUiModeAction(private val renderContext: ConfigurationHolder)
  : DropDownAction("System UI Mode", "System UI Mode", StudioIcons.DeviceConfiguration.NIGHT_MODE) {

  override fun actionPerformed(event: AnActionEvent) {
    val button = event.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) as? ActionButton ?: return
    val menu = JPopupMenu().apply {
      isLightWeightPopupEnabled = false
      isOpaque = false
      border = JBUI.Borders.empty(POPUP_VERTICAL_BORDER, 0)
    }
    menu.layout = GridBagLayout()
    val numWallpapers = enumValues<Wallpaper>().size
    val gbc = GridBagConstraints().apply {
      fill = GridBagConstraints.HORIZONTAL
      gridx = 0
      gridy = 0
      gridwidth = numWallpapers + 1
    }

    val modeTitle = TitleItem("Mode")
    menu.add(modeTitle, gbc)

    renderContext.configuration?.nightMode.let { currentNightMode ->
      enumValues<NightMode>().forEach { mode ->
        val action = SetNightModeAction(renderContext, mode.shortDisplayValue, mode, mode == currentNightMode)
        val item = ActionItem(action, event.dataContext)
        gbc.gridy += 1
        menu.add(item, gbc)
      }
    }

    gbc.gridy += 1
    val separator = JPopupMenu.Separator().apply {
      setUI(separatorUI)
    }
    menu.add(separator, gbc)

    val wallpaperTitle = TitleItem("Dynamic Color")
    gbc.gridy += 1
    menu.add(wallpaperTitle, gbc)

    val wallpaperPadding = JBUIScale.scale(WALLPAPER_ICON_PADDING)
    gbc.apply {
      gridy += 1
      gridwidth = 1
      fill = GridBagConstraints.NONE
      ipadx = wallpaperPadding
      ipady = wallpaperPadding
    }
    renderContext.configuration?.let {
      val wallpapers = mutableListOf<Wallpaper?>().apply {
        addAll(enumValues<Wallpaper>())
        add(null)
      }
      val currentWallpaper = wallpaperFromPath(it.wallpaperPath)
      wallpapers.forEachIndexed { index, wallpaper ->
        val menuItem = SetWallpaperAction(renderContext, wallpaper).toMenuItem(event, currentWallpaper == wallpaper)
        gbc.insets = when (index) {
          0 -> JBUI.insets(4, sideBorderWidth.unscaled.toInt(), 0, 0)
          numWallpapers -> JBUI.insets(4, WALLPAPER_ICON_SEPARATION, 0, sideBorderWidth.unscaled.toInt())
          else -> JBUI.insets(4, WALLPAPER_ICON_SEPARATION, 0, 0)
        }
        menu.add(menuItem, gbc)
        gbc.gridx += 1
      }
    }

    JBPopupMenu.showBelow(button, menu)
  }

  @TestOnly
  fun getWallpaperActions(): List<AnAction> {
    val actions = mutableListOf<ConfigurationAction>()
    renderContext.configuration?.let {
      enumValues<Wallpaper>().forEach { wallpaper ->
        actions.add(SetWallpaperAction(renderContext, wallpaper))
      }
      actions.add(SetWallpaperAction(renderContext, null))
    }
    return actions
  }

  @TestOnly
  fun getNightModeActions(): List<AnAction> {
    val actions = mutableListOf<ConfigurationAction>()
    renderContext.configuration?.let {
      val currentNightMode = it.nightMode
      enumValues<NightMode>().forEach { mode ->
        actions.add(SetNightModeAction(renderContext, mode.shortDisplayValue, mode, mode == currentNightMode))
      }
    }
    return actions
  }
}

private class ActionItem(action: AnAction, dataContext: DataContext) : JBMenuItem(action.templateText) {
  init {
    if (Toggleable.isSelected(action.templatePresentation)) {
      var checkmark = getIcon("checkmark")
      var selectedCheckmark = getSelectedIcon("checkmark")
      if (shouldConvertIconToDarkVariant()) {
        checkmark = IconLoader.getDarkIcon(checkmark, true)
        selectedCheckmark = IconLoader.getDarkIcon(selectedCheckmark, true)
      }
      icon = checkmark
      selectedIcon = selectedCheckmark
    }
    else {
      icon = EmptyIcon.ICON_16
      selectedIcon = EmptyIcon.ICON_16
    }

    iconTextGap = 0
    border = JBUI.Borders.empty(2, sideBorderWidth.unscaled.toInt())

    addActionListener {
      val anEvent = AnActionEvent.createFromDataContext(ActionPlaces.POPUP, action.templatePresentation, dataContext)
      action.actionPerformed(anEvent)
    }
  }

  override fun getIcon(): Icon {
    if (model.isArmed) {
      return selectedIcon
    }
    return super.getIcon()
  }

  override fun updateUI() {
    setUI(ItemUI())
  }

  private fun shouldConvertIconToDarkVariant(): Boolean {
    return JBColor.isBright() && ColorUtil.isDark(JBColor.namedColor("MenuItem.background", 0xffffff))
  }
}

private class TitleItem(title: String) : JBMenuItem(title) {
  init {
    isEnabled = false
    horizontalTextPosition = SwingConstants.LEFT
    border = JBUI.Borders.empty(TITLE_VERTICAL_BORDER, sideBorderWidth.unscaled.toInt())
  }

  override fun updateUI() {
    setUI(ItemUI())
  }
}

private class WallpaperItem(action: AbstractAction, isSelected: Boolean) : JMenuItem(action) {
  init {
    iconTextGap = 0
    horizontalAlignment = SwingConstants.LEFT
    preferredSize = JBUI.size(ICON_SIZE + 2)
    if (isSelected) {
      border = RoundedLineBorder(selectionBackground, JBUI.scale(2), JBUI.scale(2))
    }
  }

  override fun updateUI() {
    setUI(ItemUI())
  }
}

private class ItemUI : BasicMenuItemUI() {
  init {
    // This needs to be a non-null icon of size 0 for UI on macOS to match the other platforms
    checkIcon = EmptyIcon.ICON_0
  }
}

private class SetWallpaperAction(renderContext: ConfigurationHolder, val wallpaper: Wallpaper?) : ConfigurationAction(renderContext) {

  override fun updateConfiguration(configuration: Configuration, commit: Boolean) {
    configuration.wallpaperPath = wallpaper?.resourcePath
    configuration.useThemedIcon = wallpaper != null
  }

  @Suppress("UnstableApiUsage")
  fun toMenuItem(event: AnActionEvent, isSelected: Boolean): JMenuItem {
    val scaledIconSize = JBUIScale.scale(ICON_SIZE)
    val scaledWallpaperIcon = wallpaper?.let {
      RoundedIcon(IconUtil.cropIcon(it.icon, scaledIconSize, scaledIconSize), 0.1)
    } ?: getNullWallpaperIcon(scaledIconSize)
    val action = object : AbstractAction(null, scaledWallpaperIcon) {
      override fun actionPerformed(e: ActionEvent) {
        val actionEvent = AnActionEvent.createFromDataContext(ActionPlaces.POPUP, this@SetWallpaperAction.templatePresentation,
                                                              event.dataContext)
        this@SetWallpaperAction.actionPerformed(actionEvent)
      }
    }
    return WallpaperItem(action, isSelected)
  }
}

enum class Wallpaper(val resourcePath: String, val icon: Icon) {
  RED("/wallpapers/red.png", getWallpaperIcon("/wallpapers/red_thumbnail.png")),
  GREEN("/wallpapers/green.png", getWallpaperIcon("/wallpapers/green_thumbnail.png")),
  BLUE("/wallpapers/blue.png", getWallpaperIcon("/wallpapers/blue_thumbnail.png")),
  YELLOW("/wallpapers/yellow.png", getWallpaperIcon("/wallpapers/yellow_thumbnail.png")),
}

private fun wallpaperFromPath(path: String?): Wallpaper? {
  if (path == null) {
    return null
  }
  return Wallpaper.values().firstOrNull { path == it.resourcePath }
}

private fun getWallpaperIcon(path: String): Icon {
  return IconLoader.getIcon(path, Wallpaper::class.java)
}

private fun getNullWallpaperIcon(scaledIconSize: Int) = LayeredIcon(2).apply {
  setIcon(ColorIcon(scaledIconSize, JBUI.CurrentTheme.ActionButton.hoverBackground()), 0)
  val icon = StudioIcons.Common.CLEAR
  setIcon(icon, 1, (scaledIconSize - icon.iconWidth) / 2, (scaledIconSize - icon.iconHeight) / 2)
}
