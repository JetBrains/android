/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.utils

import com.android.tools.adtui.swing.findAllDescendants
import javax.swing.JComponent

internal fun findComponentWithUniqueName(root: JComponent, name: String): JComponent? {
  val matches = root.findAllDescendants<JComponent> { component -> name == component.name }
  val count = matches.count()
  check(count <= 1) { "More than one component found with the name: $name" }
  return if (count == 1) matches.first() else null
}
