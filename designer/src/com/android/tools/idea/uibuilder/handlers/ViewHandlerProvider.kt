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
package com.android.tools.idea.uibuilder.handlers

import com.android.tools.idea.uibuilder.api.ViewHandler

/**
 * Extension point that allows returning custom [ViewHandler]s for a given tag name.
 *
 * The Layout Editor will only call the providers if no built-in ViewHandler is found.
 */
interface ViewHandlerProvider {
  /**
   * Returns a [ViewHandler] for the given [viewTag] or null if none is available from this provider
   */
  fun findHandler(viewTag: String): ViewHandler?
}