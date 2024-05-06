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
package com.android.tools.idea.databinding

import com.google.common.collect.ImmutableList
import com.intellij.openapi.util.UserDataHolderBase

/**
 * A collection of relevant information for one (or more) related layout XML files - that is, a base
 * layout with possible alternate (e.g. landscape) configurations.
 */
class BindingLayoutGroup(layouts: Collection<BindingLayout>) : UserDataHolderBase() {
  init {
    assert(layouts.isNotEmpty())
  }

  val layouts: List<BindingLayout> = ImmutableList.copyOf(layouts)

  val mainLayout: BindingLayout
    // Safe to assume there should always be at least one layout in a group.
    get() =
      layouts.firstOrNull { layout -> layout.resource.configuration.isDefault } ?: layouts.first()

  override fun equals(other: Any?): Boolean {
    return other is BindingLayoutGroup && layouts == other.layouts
  }

  override fun hashCode(): Int {
    return layouts.hashCode()
  }
}
