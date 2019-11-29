/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.adtui.LightCalloutPopup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.psi.PsiFile
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet

/**
 * Action for adding custom configuration into the given [CustomModelsProvider].
 * For now the implementation is showing [ConfigurationCreationPalette] as a popup dialog and add the configuration picked from it.
 */
class AddCustomConfigurationAction(private val file: PsiFile,
                                   private val facet: AndroidFacet,
                                   private val provider: CustomModelsProvider)
  : AnAction("Add Configuration", "Adding a custom configuration", StudioIcons.NavEditor.Toolbar.ADD_DESTINATION) {

  override fun actionPerformed(e: AnActionEvent) {
    val dialog = LightCalloutPopup()

    val content = ConfigurationCreationPalette(file, facet) { name, newConfig ->
      provider.addConfiguration(CustomConfiguration(name, newConfig))
      dialog.close()
    }
    val owner = e.inputEvent.component
    val location = owner.locationOnScreen
    location.translate(owner.width / 2, owner.height)

    dialog.show(content, null, location)
  }
}
