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
package com.android.tools.idea.adddevicedialog

import com.android.tools.idea.grouplayout.GroupLayout.Companion.groupLayout
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import javax.swing.GroupLayout
import javax.swing.JButton

internal class AdditionalSettingsPanel internal constructor() : JBPanel<AdditionalSettingsPanel>(null) {
  init {
    val sdkExtensionLevelLabel = JBLabel("SDK extension level")
    val sdkExtensionLevelComboBox = ComboBox<Any>()

    val deviceSkinLabel = JBLabel("Device skin")
    val deviceSkinComboBox = ComboBox<Any>()
    val importButton = JButton("Import")

    val cameraSeparator = TitledSeparator("Camera")

    val frontLabel = JBLabel("Front")
    val frontComboBox = ComboBox<Any>()

    val rearLabel = JBLabel("Rear")
    val rearComboBox = ComboBox<Any>()

    val networkSeparator = TitledSeparator("Network")

    val speedLabel = JBLabel("Speed")
    val speedComboBox = ComboBox<Any>()

    val latencyLabel = JBLabel("Latency")
    val latencyComboBox = ComboBox<Any>()

    val startupSeparator = TitledSeparator("Startup")

    val orientationLabel = JBLabel("Orientation")
    val orientationComboBox = ComboBox<Any>()

    val defaultBootLabel = JBLabel("Default boot")
    val defaultBootComboBox = ComboBox<Any>()

    layout = groupLayout(this) {
      horizontalGroup {
        parallelGroup {
          sequentialGroup {
            component(sdkExtensionLevelLabel)
            component(sdkExtensionLevelComboBox)
          }

          sequentialGroup {
            component(deviceSkinLabel)
            component(deviceSkinComboBox)
            component(importButton)
          }

          component(cameraSeparator)

          sequentialGroup {
            component(frontLabel)
            component(frontComboBox)
          }

          sequentialGroup {
            component(rearLabel)
            component(rearComboBox)
          }

          component(networkSeparator)

          sequentialGroup {
            component(speedLabel)
            component(speedComboBox)
          }

          sequentialGroup {
            component(latencyLabel)
            component(latencyComboBox)
          }

          component(startupSeparator)

          sequentialGroup {
            component(orientationLabel)
            component(orientationComboBox)
          }

          sequentialGroup {
            component(defaultBootLabel)
            component(defaultBootComboBox)
          }
        }
      }

      verticalGroup {
        sequentialGroup {
          parallelGroup {
            component(sdkExtensionLevelLabel)
            component(sdkExtensionLevelComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          parallelGroup {
            component(deviceSkinLabel)
            component(deviceSkinComboBox, max = GroupLayout.PREFERRED_SIZE)
            component(importButton)
          }

          component(cameraSeparator, max = GroupLayout.PREFERRED_SIZE)

          parallelGroup {
            component(frontLabel)
            component(frontComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          parallelGroup {
            component(rearLabel)
            component(rearComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          component(networkSeparator, max = GroupLayout.PREFERRED_SIZE)

          parallelGroup {
            component(speedLabel)
            component(speedComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          parallelGroup {
            component(latencyLabel)
            component(latencyComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          component(startupSeparator, max = GroupLayout.PREFERRED_SIZE)

          parallelGroup {
            component(orientationLabel)
            component(orientationComboBox, max = GroupLayout.PREFERRED_SIZE)
          }

          parallelGroup {
            component(defaultBootLabel)
            component(defaultBootComboBox, max = GroupLayout.PREFERRED_SIZE)
          }
        }
      }
    }
  }
}
