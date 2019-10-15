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
package com.android.tools.idea.npw.template.components

import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.template.Parameter
import com.google.common.base.Strings
import javax.swing.JComponent

/**
 * A class responsible for converting a [Parameter] to a [JComponent]. Any parameter
 * that represents a value (most of them, except for e.g. SEPARATOR should
 * be sure to also create an appropriate Swing property to control the component.
 */
abstract class ParameterComponentProvider2<T : JComponent> protected constructor(private val parameter: Parameter<*>) : ComponentProvider<T>() {
  override fun createComponent(): T = createComponent(parameter).apply {
    toolTipText = WizardUtils.toHtmlString(Strings.nullToEmpty(parameter.help))
  }

  protected abstract fun createComponent(parameter: Parameter<*>): T
}
