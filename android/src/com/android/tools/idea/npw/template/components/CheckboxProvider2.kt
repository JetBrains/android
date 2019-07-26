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

import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.ui.SelectedProperty
import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.Parameter
import javax.swing.JCheckBox

/**
 * Provides a checkbox well suited for handling [BooleanParameter].
 */
class CheckboxProvider2(parameter: BooleanParameter) : ParameterComponentProvider2<JCheckBox>(parameter) {
  override fun createComponent(parameter: Parameter<*>): JCheckBox = JCheckBox(parameter.name)
  override fun createProperty(component: JCheckBox): AbstractProperty<*>? = SelectedProperty(component)
}
