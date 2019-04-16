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
import com.android.tools.idea.gradle.structure.model.PsJarDependency
import javax.swing.JPanel

class JarDependencyDetails(
  private val myContext: PsContext,
  private val showScope: Boolean
) : JarDependencyDetailsForm(), DependencyDetails {

  init {
    myScopeLabel.isVisible = showScope
    myScopeText.isVisible = showScope
  }

  private var myDependency: PsJarDependency? = null

  override fun getPanel(): JPanel {
    return myMainPanel
  }

  override fun display(dependency: PsBaseDependency) {
    myDependency = dependency as PsJarDependency
    myNameText.text = myDependency!!.name
    myIncludesText.text = myDependency!!.includes.toString()
    myExcludesText.text = myDependency!!.excludes.toString()
    myScopeText.text = myDependency!!.joinedConfigurationNames
  }

  override fun getSupportedModelType(): Class<PsJarDependency> {
    return PsJarDependency::class.java
  }

  override fun getModel(): PsJarDependency? {
    return myDependency
  }
}
