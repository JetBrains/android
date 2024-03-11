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
package com.android.tools.idea.flags.overrides

import com.android.flags.BooleanFlag
import com.android.flags.Flag
import com.android.flags.ImmutableFlagOverrides
import com.android.flags.IntFlag
import com.android.tools.idea.serverflags.ServerFlagService

/*
ServerFlagOverrides is used to override StudioFlags from
the server. The server flag name is equal to the studio
flag name prefaced by "studio_flags/".
 */
internal class ServerFlagOverrides : ImmutableFlagOverrides {

  override fun get(flag: Flag<*>): String? {
    val service = ServerFlagService.instance
    val id = flag.id
    val name = "studio_flags/$id"

    // Currently, only boolean and integer flag overrides are supported.
    return when(flag) {
      is BooleanFlag -> service.getBoolean(name)?.toString()
      is IntFlag -> service.getInt(name)?.toString()
      else -> null
    }
  }
}