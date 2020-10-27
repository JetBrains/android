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
package com.android.tools.idea.gradle.structure.configurables

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.components.JBLabel
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Configurable placeholder to show the message that PSD support is limited for projects with KTS.
 */
class KtsProjectPerspectiveConfigurable() :
  Place.Navigator, Configurable {

  override fun getDisplayName(): String = "Project"

  override fun createComponent(): JComponent? {
    val firstLine = "Project Structure is unavailable for projects that use Gradle KTS build files."
    val secondLine = "This project uses Gradle KTS build files which are not fully supported in this version of Android Studio."
    return JPanel().apply {
      layout = FlowLayout(FlowLayout.LEADING)
      preferredSize = JBUI.size(700, 500)
      add(JBLabel().apply {
        text = "<html><div><b>$firstLine</b><br/><br/>$secondLine</div></html>"
        border = JBUI.Borders.empty(20)
      })
    }
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean) = ActionCallback.DONE

  override fun apply() = Unit

  override fun isModified(): Boolean = false

}