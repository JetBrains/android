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
package com.android.tools.res.ids

import com.android.tools.res.ResourceNamespacing

/** Representation of an Android module required for a [ResourceIdManager]. */
interface ResourceIdManagerModelModule {
  val isAppOrFeature: Boolean

  val namespacing: ResourceNamespacing

  companion object {
    @JvmField
    val NO_NAMESPACING_APP = object : ResourceIdManagerModelModule {
      override val isAppOrFeature: Boolean = true
      override val namespacing: ResourceNamespacing = ResourceNamespacing.DISABLED
    }
  }
}