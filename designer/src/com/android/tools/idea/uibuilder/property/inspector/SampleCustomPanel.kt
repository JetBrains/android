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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.api.CustomPanel
import com.android.tools.property.panel.api.InspectorBuilder
import javax.swing.JPanel

/**
 * Sample [CustomPanel] used in the Nele [InspectorBuilder]s.
 *
 * The [InspectorBuilder] can lazily generate a [CustomPanel]. Use the [INSTANCE] to identify a non
 * existing [CustomPanel] such that the [InspectorBuilder] only have to attempt to generate it once.
 * See [LayoutInspectorBuilder].
 */
class SampleCustomPanel private constructor() : CustomPanel {

  override fun getPanel(): JPanel {
    throw NotImplementedError()
  }

  override fun useComponent(component: NlComponent?, surface: DesignSurface<*>?) {
    throw NotImplementedError()
  }

  override fun refresh() {
    throw NotImplementedError()
  }

  companion object {
    @JvmField val INSTANCE = SampleCustomPanel()
  }
}
