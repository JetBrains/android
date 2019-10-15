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

import com.android.tools.adtui.LabelWithEditButton
import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.wizard.template.Parameter
import com.android.tools.idea.wizard.template.StringParameter

/**
 * Provides a [LabelWithEditButton] for more advanced [StringParameter] that only users who know what they're doing should modify.
 */
class LabelWithEditButtonProvider2(parameter: StringParameter) : ParameterComponentProvider2<LabelWithEditButton>(parameter) {
  override fun createComponent(parameter: Parameter<*>): LabelWithEditButton = LabelWithEditButton()
  override fun createProperty(component: LabelWithEditButton): AbstractProperty<*> = TextProperty(component)
}
