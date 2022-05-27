/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeModuleSourceSet
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import java.io.Serializable

data class IdeModuleSourceSetImpl(
  override val sourceSetName: String,
  override val canBeConsumed: Boolean
) : IdeModuleSourceSet, Serializable {
  init {
    if (sourceSetName.isEmpty()) error("sourceSetName cannot be empty")
    if (IdeModuleWellKnownSourceSet.values().any { it.sourceSetName == sourceSetName }) {
      error("'$sourceSetName' is a well-known source set name. ")
    }
  }

  companion object {
    fun wellKnownOrCreate(name: String): IdeModuleSourceSet {
      return IdeModuleWellKnownSourceSet.fromName(name)
        ?: IdeModuleSourceSetImpl(name, canBeConsumed = true /* We don't know so we assume true. */)
    }
  }

  override fun toString(): String {
    return "${sourceSetName}${if (!canBeConsumed) "(non-consumable)" else ""}"
  }
}
