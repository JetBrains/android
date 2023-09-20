/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.Resource

class StringTableImpl(strings: List<LayoutInspectorViewProtocol.StringEntry>) : StringTable {
  private val table = strings.associateBy({ it.id }, { it.str })
  override val keys: Set<Int> = table.keys

  override operator fun get(id: Int): String = table[id].orEmpty()

  operator fun get(resource: Resource?): ResourceReference? {
    return resource?.convert()?.createReference(this)
  }
}
