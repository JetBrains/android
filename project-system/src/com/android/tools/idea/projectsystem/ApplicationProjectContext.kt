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
package com.android.tools.idea.projectsystem

import com.android.ddmlib.Client
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * A context object describing a running Android app (or more generally a process that includes tests).
 *
 * There are two major ways an instance of [ApplicationProjectContext] is supposed ot be obtained:
 *
 *   (1) When the IDE launches a new Android process, the project system specific execution subsystem is supposed to instantiate and pass
 *   a new instance of [ApplicationProjectContext] to the caller. Normally it is a part of run configuration support.
 *
 *   (2) When the IDE attaches to an already running Android process, the [ApplicationProjectContextProvider] should be used to obtain
 *   an instance of [ApplicationProjectContext] in a project system specific way.
 *
 * **Note**: Project systems might need to recognise their own instances and reject other implementation
 */
interface ApplicationProjectContext {
  val applicationId: String
}

/**
 * A provider that knows how to obtain an [ApplicationProjectContext] for a given [Client] in the context of a specific
 * [AndroidProjectSystem].
 *
 * Note: this mechanism is applicable when the IDE is attaching to an already Running process. When deploying and launching a new process
 *       the project/build system should construct the [ApplicationProjectContext] directly.
 */
interface ApplicationProjectContextProvider: Token {
  companion object {
    val EP_NAME =
      ExtensionPointName<ApplicationProjectContextProvider>("com.android.tools.idea.projectsystem.ApplicationProjectContextProvider")

    @JvmStatic
    fun AndroidProjectSystem.getApplicationProjectContextProvider(): ApplicationProjectContextProvider = getToken(EP_NAME)

    @JvmStatic
    fun AndroidProjectSystem.getApplicationProjectContext(client: Client): ApplicationProjectContext? =
      getApplicationProjectContextProvider().getApplicationProjectContext(client)
  }

  fun getApplicationProjectContext(client: Client): ApplicationProjectContext?
}
