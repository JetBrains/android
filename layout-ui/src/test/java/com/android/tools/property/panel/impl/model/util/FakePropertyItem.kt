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
package com.android.tools.property.panel.impl.model.util

import com.android.SdkConstants
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.property.panel.api.ActionIconButton
import com.android.tools.property.panel.api.PropertyItem
import com.android.utils.HashCodes
import icons.StudioIcons
import javax.swing.Icon

/** [PropertyItem] used in tests. */
open class FakePropertyItem(
  override var namespace: String,
  override var name: String,
  initialValue: String? = null,
  override var browseButton: ActionIconButton? = null,
  override var colorButton: ActionIconButton? = null,
  override val editingSupport: EditingSupport = EditingSupport.INSTANCE,
) : PropertyItem {

  override var isReference: Boolean = false

  override val namespaceIcon: Icon?
    get() =
      if (namespace == SdkConstants.TOOLS_URI) StudioIcons.LayoutEditor.Properties.TOOLS_ATTRIBUTE
      else null

  private var _value: String? = initialValue

  override var value
    get() = _value
    set(value) {
      _value = value
      resolvedValue = value
      updateCount++
    }

  fun emulateLateValueUpdate(oldValue: String?) {
    _value = oldValue
  }

  override var defaultValue: String? = null

  override var resolvedValue: String? = initialValue

  override var tooltipForName = ""

  override var tooltipForValue = ""

  var updateCount = 0
    protected set

  override fun equals(other: Any?) =
    when (other) {
      is FakePropertyItem -> namespace == other.namespace && name == other.name
      else -> false
    }

  override fun hashCode() = HashCodes.mix(namespace.hashCode(), name.hashCode())
}
