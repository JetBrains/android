/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.projectmodel.ExternalAndroidLibrary
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module

/**
 * Implementors of this project system [Token] interface are responsible for implementing methods to provide information about
 * a particular module for display to help users (and Android Studio developers) troubleshoot issues through the
 * `Help|Diagnostic Tools|Collect Troubleshooting Information` action.
 */
interface LibraryDependenciesTroubleInfoCollectorToken<P: AndroidModuleSystem> : Token {
  /** return the collection of [ExternalAndroidLibrary]s this module depends on. */
  fun getDependencies(moduleSystem: P, module: Module): Collection<ExternalAndroidLibrary>
  /** return a string of key=value properties specific to the project/module system. */
  fun getInfoString(module: Module): String
  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName<LibraryDependenciesTroubleInfoCollectorToken<AndroidModuleSystem>>(
      "com.android.tools.idea.projectsystem.libraryDependenciesTroubleInfoCollectorToken")
  }
}