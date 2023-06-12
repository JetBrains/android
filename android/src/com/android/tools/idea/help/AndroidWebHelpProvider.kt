/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.help

import com.google.common.base.Verify
import com.intellij.openapi.help.WebHelpProvider

/**
 * An Android-specific provider which routes help topics to developer.android.com.
 */
class AndroidWebHelpProvider : WebHelpProvider() {

  override fun getHelpPageUrl(helpTopicId: String): String? {
    Verify.verify(HELP_PREFIX == helpTopicPrefix)

    return HELP_URL + helpTopicId.removePrefix(helpTopicPrefix)
  }

  companion object {
    const val HELP_PREFIX = "org.jetbrains.android."
    const val HELP_URL = "https://developer.android.com/"
  }
}
