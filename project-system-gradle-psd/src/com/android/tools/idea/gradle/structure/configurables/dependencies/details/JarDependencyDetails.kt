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
import javax.swing.JPanel

class JarDependencyDetails(
  private val myContext: PsContext,
  showScope: Boolean
) : JarDependencyDetailsForm() {

  init {
    myConfigurationLabel.isVisible = showScope
    myConfigurationPanel.isVisible = showScope
  }

  private var myDependency: PsJarDependency? = null

  override fun getPanel(): JPanel {
    return myMainPanel
  }

  override fun display(dependency: PsBaseDependency) {
    val d = dependency as PsJarDependency
    if (myConfigurationPanel.isVisible) {
      displayConfiguration(d as PsDeclaredJarDependency, PsModule.ImportantFor.LIBRARY)
    }
    if (d != myDependency) {
      myNameText.text = dependency.name
      myIncludesText.text = dependency.includes.toString()
      myExcludesText.text = dependency.excludes.toString()
    }
    myDependency = d
  }

  override fun getSupportedModelType(): Class<PsJarDependency> {
    return PsJarDependency::class.java
  }

  override fun getModel(): PsJarDependency? {
    return myDependency
  }

  override fun getContext(): PsContext {
    return myContext
  }
}
