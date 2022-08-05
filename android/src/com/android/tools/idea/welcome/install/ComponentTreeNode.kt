/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.install

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep
import com.android.tools.idea.wizard.model.ModelWizardStep

/**
 * Component that may be installed by the first run wizard.
 */
abstract class ComponentTreeNode(val description: String) {
  abstract val label: String
  abstract val childrenToInstall: Collection<InstallableComponent>
  abstract val isChecked: Boolean
  abstract val immediateChildren: Collection<ComponentTreeNode>
  val allChildren: Collection<ComponentTreeNode>
    get() = listOf(this).plus(immediateChildren.flatMap { it.allChildren })

  abstract val isEnabled: Boolean
  abstract val steps: Collection<ModelWizardStep<*>>

  override fun toString(): String = label
  open fun init(progressStep: ProgressStep) {  }
  abstract fun updateState(handler: AndroidSdkHandler)
  @Deprecated("this is for an old welcome wizard", ReplaceWith("step"))
  abstract fun createSteps(): Collection<DynamicWizardStep>
  abstract fun toggle(isSelected: Boolean)
}
