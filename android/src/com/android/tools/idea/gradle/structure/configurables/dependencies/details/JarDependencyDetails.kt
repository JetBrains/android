/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsBaseDependency
import com.android.tools.idea.gradle.structure.model.PsDeclaredJarDependency
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JPanel

class JarDependencyDetails(
  private val myContext: PsContext,
  showScope: Boolean
) : JarDependencyDetailsForm(), DependencyDetails {

  init {
    myScopeLabel.isVisible = showScope
    myScope.isVisible = showScope
  }

  private var myDependency: PsJarDependency? = null

  override fun getPanel(): JPanel {
    return myMainPanel
  }

  override fun display(dependency: PsBaseDependency) {
    val d = dependency as PsJarDependency
    displayConfiguration(d)
    if (d != myDependency) {
      myNameText.text = dependency.name
      myIncludesText.text = dependency.includes.toString()
      myExcludesText.text = dependency.excludes.toString()
    }
    myDependency = d
  }

  private fun displayConfiguration(dependency: PsJarDependency) {
    if (dependency != myDependency) {
      try {
        comboMaintenance = true
        myScope.removeAllItems()
        val configuration = dependency.joinedConfigurationNames
        myScope.addItem(configuration)
        dependency.parent.getConfigurations(PsModule.ImportantFor.LIBRARY)
          .filter { it != configuration }
          .forEach { myScope.addItem(it) }
      } finally {
        comboMaintenance = false
      }
    }
  }

  override fun getSupportedModelType(): Class<PsJarDependency> {
    return PsJarDependency::class.java
  }

  override fun getModel(): PsJarDependency? {
    return myDependency
  }

  // TODO(xof): duplicate code with {Module,SingleLibrary}DependencyDetails
  override fun modifyConfiguration() {
    if (myDependency != null && myScope.selectedItem != null) {
      val selectedConfiguration = myScope.selectedItem as? String
      if (selectedConfiguration != null) {
        val module = myDependency!!.parent
        module.modifyDependencyConfiguration(myDependency as PsDeclaredJarDependency, selectedConfiguration)
      }
    }
  }
}
