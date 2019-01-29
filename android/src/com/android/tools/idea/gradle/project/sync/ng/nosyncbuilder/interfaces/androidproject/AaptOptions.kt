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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto


/** Options for aapt. */
interface AaptOptions {
  enum class Namespacing {
    /**
     * Resources are not namespaced.
     *
     * They are merged at the application level, as was the behavior with AAPT1.
     */
    DISABLED,
    /**
     * Resources must be namespaced.
     *
     * Each library is compiled in to an AAPT2 static library with its own namespace.
     *
     * Projects using this *cannot* consume non-namespaced dependencies.
     */
    REQUIRED,
  }

  /** The resource namespacing strategy for this sub-project */
  val namespacing: Namespacing

  fun toProto() = AndroidProjectProto.AaptOptions.newBuilder()
    .setNamespacing(AndroidProjectProto.AaptOptions.Namespacing.valueOf(namespacing.name))
    .build()!!
}
