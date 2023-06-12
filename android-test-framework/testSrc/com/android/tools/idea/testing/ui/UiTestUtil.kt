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
package com.android.tools.idea.testing.ui

import java.awt.Component
import java.awt.Container

fun Component.flatten(visibleOnly: Boolean = true): List<Component> {
  if (visibleOnly && !isVisible) return emptyList()

  if (this !is Container) {
    return listOf(this)
  }
  return components.flatMap { it.flatten(visibleOnly) }.plus(this)
}
