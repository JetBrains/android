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
package com.android.tools.idea.compose.preview.util

import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN

internal fun findComposeViewAdapter(viewObj: Any): Any? {
  if (COMPOSE_VIEW_ADAPTER_FQN == viewObj.javaClass.name) {
    return viewObj
  }

  val childrenCount = viewObj.javaClass.getMethod("getChildCount").invoke(viewObj) as Int
  for (i in 0 until childrenCount) {
    val child =
      viewObj.javaClass.getMethod("getChildAt", Int::class.javaPrimitiveType).invoke(viewObj, i)
    return findComposeViewAdapter(child)
  }
  return null
}
