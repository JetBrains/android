/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.ide.common.gradle.Dependency
import com.android.tools.idea.gradle.model.IdeDeclaredDependencies
import org.jetbrains.annotations.VisibleForTesting

data class IdeDeclaredDependenciesImpl(
  override val configurationsToCoordinates: Map<String, List<IdeCoordinatesImpl>>
): IdeDeclaredDependencies {
  @VisibleForTesting
  constructor(list: List<String>): this(mapOf("implementation" to list.map { it.toIdeCoordinates() }))

  data class IdeCoordinatesImpl(override val group: String?, override val name: String, override val version: String?):
    IdeDeclaredDependencies.IdeCoordinates

  companion object {
    private fun String.toIdeCoordinates() = Dependency.Companion.parse(this).let { IdeCoordinatesImpl(it.group, it.name, it.version?.toString()) }
  }
}