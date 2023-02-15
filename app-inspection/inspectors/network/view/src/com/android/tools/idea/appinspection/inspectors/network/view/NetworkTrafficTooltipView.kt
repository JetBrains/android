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
package com.android.tools.idea.appinspection.inspectors.network.view

import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.LegendConfig
import com.android.tools.adtui.TooltipView
import com.android.tools.idea.appinspection.inspectors.network.model.LegendsModel
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkTrafficTooltipModel
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_RECEIVING_COLOR
import com.android.tools.idea.appinspection.inspectors.network.view.constants.NETWORK_SENDING_COLOR
import javax.swing.JComponent

class NetworkTrafficTooltipView
internal constructor(view: NetworkInspectorView, private val tooltip: NetworkTrafficTooltipModel) :
  TooltipView(view.model.timeline) {
  override fun createTooltip(): JComponent {
    val legends: LegendsModel = tooltip.getLegends()
    val legend: LegendComponent =
      LegendComponent.Builder(legends)
        .setVerticalPadding(0)
        .setOrientation(LegendComponent.Orientation.VERTICAL)
        .build()
    legend.configure(
      legends.rxLegend,
      LegendConfig(LegendConfig.IconType.BOX, NETWORK_RECEIVING_COLOR)
    )
    legend.configure(
      legends.txLegend,
      LegendConfig(LegendConfig.IconType.BOX, NETWORK_SENDING_COLOR)
    )
    return legend
  }
}
