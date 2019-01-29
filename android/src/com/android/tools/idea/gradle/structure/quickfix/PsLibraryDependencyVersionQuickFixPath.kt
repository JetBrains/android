/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.quickfix

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.issues.QuickFixLinkHandler.QUICK_FIX_PATH_TYPE
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsQuickFix
import com.android.tools.idea.gradle.structure.quickfix.QuickFixes.QUICK_FIX_PATH_SEPARATOR
import com.android.tools.idea.gradle.structure.quickfix.QuickFixes.SET_LIBRARY_DEPENDENCY_QUICK_FIX

const val DEFAULT_QUICK_FIX_TEXT = "[Fix]"
data class PsLibraryDependencyVersionQuickFixPath(
  val moduleName: String,
  val dependency: String,
  val configurationName: String,
  val version: String,
  val text: String
) : PsQuickFix {

  constructor(
    dependency: PsLibraryDependency,
    version: String,
    quickFixText: String = DEFAULT_QUICK_FIX_TEXT
  ) : this(
    dependency.parent.name, dependency.spec.compactNotation(), dependency.joinedConfigurationNames, version, quickFixText)

  override fun getHyperlinkDestination(context: PsContext): String =
    QUICK_FIX_PATH_TYPE +
    (listOfNotNull(SET_LIBRARY_DEPENDENCY_QUICK_FIX, moduleName, dependency, configurationName, version)
      .joinToString(QUICK_FIX_PATH_SEPARATOR.toString()))

  override fun getHtml(context: PsContext): String = "<a href=\"${getHyperlinkDestination(context)}\">$text</a>"

  override fun toString(): String = "$dependency ($configurationName)"
}
