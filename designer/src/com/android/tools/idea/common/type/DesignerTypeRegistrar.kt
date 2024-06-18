/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.type

import com.android.tools.idea.common.type.DesignerTypeRegistrar.register
import java.util.concurrent.CopyOnWriteArraySet
import org.jetbrains.annotations.TestOnly

/**
 * Responsible for registering supported [DesignerEditorFileType]s. Types should be registered using
 * the [register] method, as the list returned when fetching the registered types is immutable.
 */
object DesignerTypeRegistrar {

  private val types: MutableSet<DesignerEditorFileType> = CopyOnWriteArraySet()

  val registeredTypes: Set<DesignerEditorFileType>
    get() = types

  fun register(type: DesignerEditorFileType) {
    types.add(type)
  }

  /** Use this function to clear the registered types during testing. */
  @TestOnly
  fun clearRegisteredTypes() {
    types.clear()
  }
}
