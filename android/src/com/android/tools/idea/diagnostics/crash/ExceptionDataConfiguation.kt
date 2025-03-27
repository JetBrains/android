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
package com.android.tools.idea.diagnostics.crash

import com.android.tools.idea.serverflags.ServerFlagService
import com.android.tools.idea.serverflags.protos.ExceptionConfiguration
import com.intellij.openapi.application.ApplicationManager

interface ExceptionDataConfiguration {
  companion object {
    @JvmStatic
    fun getInstance(): ExceptionDataConfiguration {
      return ApplicationManager.getApplication().getService(ExceptionDataConfiguration::class.java)
    }
  }

  fun getConfigurations(): Map<String, ExceptionConfiguration>
}

class ExceptionDataConfigurationImpl : ExceptionDataConfiguration {
  companion object {
    private const val FLAG_NAME_PREFIX = "exceptions/"
  }

  private val flagService = ServerFlagService.instance

  override fun getConfigurations() = flagService.flagAssignments.keys
    .filter { it.startsWith(FLAG_NAME_PREFIX) }.associate {
      it.substring(FLAG_NAME_PREFIX.length) to flagService.getProto(it, ExceptionConfiguration.getDefaultInstance())
    }
}