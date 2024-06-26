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
package com.android.tools.idea.npw.module.recipes.androidProject

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.wizard.template.renderIf
import java.net.URL

fun androidProjectGradleSettings(appTitle: String,
                                 agpVersion: AgpVersion,
                                 useGradleKts: Boolean,
                                 injectedRepositories: List<URL>): String {
  require(!appTitle.contains("\\")) { "Backslash should not be present in the application title" }
  return renderIf(appTitle.isNotBlank()) {
    val escapedAppTitle = appTitle.replace("$", "\\$")
    val injectedRepositoriesSnippet =
      if(injectedRepositories.isEmpty()) "" else injectedRepositories.joinToString("") { "\n    maven { url = uri(\"${it}\") }" }

    """
pluginManagement {
  repositories {$injectedRepositoriesSnippet
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {$injectedRepositoriesSnippet
    google()
    mavenCentral()
  }
}
""".gradleSettingsToKtsIfKts(useGradleKts) +
    """
rootProject.name = "$escapedAppTitle"
"""

  }
}

private fun String.gradleSettingsToKtsIfKts(isKts: Boolean): String = if (isKts) {
  split("\n").joinToString("\n") {
    it.replace("'", "\"")
      .replace("id ", "id(").replace(" version", ") version")
  }
}
else {
  this
}
