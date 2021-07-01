// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.android.cwm

import com.jetbrains.ide.model.uiautomation.BeControl
import com.jetbrains.ide.model.uiautomation.BeGrid
import com.jetbrains.ide.model.uiautomation.BeGridElement
import com.jetbrains.ide.model.uiautomation.BeSizingType
import com.jetbrains.ide.model.uiautomation.BeSpacer
import com.jetbrains.ide.model.uiautomation.BeUnitSize
import com.jetbrains.ide.model.uiautomation.GridOrientation
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rdserver.ui.converters.RdConverter
import com.jetbrains.rdserver.ui.converters.TrackOptions
import com.jetbrains.rdserver.ui.converters.toBeModel
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JPanel

class DeviceAndSearchPanelConverter : RdConverter<JPanel /* Real type is package-private DeviceAndSearchPanel */> {

  override fun convert(component: JPanel, trackOptions: TrackOptions, lifetime: Lifetime): BeControl {
    val components = component.components.asList()

    val gridElements = components.filter { it.isVisible }.map { it.toGridElement() }
    if (gridElements.isEmpty()) return BeSpacer()
    if (gridElements.count() == 1) return gridElements.first().content

    val beGrid = BeGrid(GridOrientation.Horizontal)
    beGrid.items.set(gridElements)
    return beGrid
  }

  private fun Component.toGridElement(): BeGridElement =
    BeGridElement(this.toBeModel(), BeUnitSize(if (this is JCheckBox) BeSizingType.Fit else BeSizingType.Fill))
}
