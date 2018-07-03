/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.configurables.PsContext

import com.android.tools.idea.gradle.structure.model.PsPath.TexType.FOR_COMPARE_TO

/**
 * A UI independent reference to a place in a build configuration.
 */
interface PsPath : Comparable<PsPath> {

  /**
   * Returns the parent context path.
   *
   * For example, a module would be a parent for its dependencies.
   */
  val parent: PsPath? get() = null


  fun toText(type: TexType): String
  fun getHyperlinkDestination(context: PsContext): String?

  override fun compareTo(other: PsPath): Int = toText(FOR_COMPARE_TO).compareTo(other.toText(FOR_COMPARE_TO))

  enum class TexType {
    PLAIN_TEXT, FOR_COMPARE_TO
  }
}
  /**
   * Returns a list of parent context paths.
   *
   * For example, a module would be a parent for its dependencies.
   */
val PsPath.parents: List<PsPath> get() = parent?.let { it.parents + it } ?: listOf()
