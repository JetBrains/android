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
package com.android.tools.compose.debug.utils

import com.android.testutils.MockitoKt
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.jdi.GeneratedLocation
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType

class MockMethod(
  private val name: String,
  private val signature: String?,
  private val argumentTypeNames: List<String>,
  private val lines: List<Int>,
  private val declaringType: ReferenceType,
  private val debugProcess: DebugProcessImpl,
) : Method by MockitoKt.mock() {
  override fun name() = name

  override fun declaringType(): ReferenceType = declaringType

  override fun allLineLocations(): List<Location> =
    lines.map { GeneratedLocation(declaringType, name, it) }

  override fun argumentTypeNames(): List<String> = argumentTypeNames

  override fun signature(): String? = signature
}
