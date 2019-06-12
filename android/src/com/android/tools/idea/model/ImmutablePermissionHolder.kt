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
package com.android.tools.idea.model

import com.android.sdklib.AndroidVersion
import com.android.tools.lint.checks.PermissionHolder

/**
 * A [PermissionHolder.SetPermissionLookup] that covers the permissions granted to a module.
 *
 * Each [ImmutablePermissionHolder] instance is backed by pre-computed immutable [Set]s which are
 * accessible to callers without any additional heavy lifting. This is in contrast to
 * [PermissionHolder.SetPermissionLookup], which may be backed with mutable sets that change over
 * time, and generic [PermissionHolder]s, for which obtaining the set of granted permissions may
 * involve non-trivial overhead.
 */
class ImmutablePermissionHolder(
  minSdk: AndroidVersion,
  targetSdk: AndroidVersion,
  val permissions: Set<String>,
  val revocable: Set<String>
): PermissionHolder.SetPermissionLookup(permissions, revocable, minSdk, targetSdk) {
  companion object {
    @JvmField val EMPTY = ImmutablePermissionHolder(AndroidVersion.DEFAULT, AndroidVersion.DEFAULT, setOf(), setOf())
  }
}