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
package com.android.tools.idea.gradle.project.upgrade

import com.google.common.annotations.VisibleForTesting

internal val List<String>.descriptionText: String
  get() {
    var col = 0
    val sb = StringBuilder()
    val last = this.size - 1
    run done@{
      this.forEachIndexed moduleNames@{ index, name ->
        when (index) {
          0 -> name.let { col += it.length; sb.append(it) }
          last -> name.let { if (index != 1) sb.append(","); sb.append(" and $it") }
          in 1..7 -> {
            sb.append(", ")
            if (col > 72) {
              sb.append("\n"); col = 0
            }
            col += name.length
            sb.append(name)
          }
          else -> this.let { sb.append(", and ${it.size - index} other modules"); return@done }
        }
      }
    }
    sb.append(".")
    return sb.toString()
  }

@VisibleForTesting
fun computeDescriptionTextForTests(list: List<String>) = list.descriptionText