/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.displayList

import com.intellij.openapi.project.Project
import javax.swing.JPanel

// Based on AsyncDevicesGetter

class PhysicalDisplayList(val project: Project?): JPanel() {
  // Commented out because it's not being used at the moment, to avoid adding the public modifier to AsyncDevicesGetter
  //val asyncDevicesGetter = AsyncDevicesGetter.getInstance(project!!)

  init {
    val x = 1
    println(x)
  }
}
