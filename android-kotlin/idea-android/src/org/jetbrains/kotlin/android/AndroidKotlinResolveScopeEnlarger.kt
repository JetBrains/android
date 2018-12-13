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
package org.jetbrains.kotlin.android

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import com.intellij.psi.search.SearchScope
import org.jetbrains.android.AndroidResolveScopeEnlarger
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.findAdditionalClassesForModule
import org.jetbrains.android.AndroidResolveScopeEnlarger.Companion.getResolveScopeForAdditionalClasses
import org.jetbrains.kotlin.idea.caches.resolve.util.KotlinResolveScopeEnlarger

/**
 * Implementation of KotlinResolveScopeEnlarger, used to add additional
 * classes, e.g., light classes, to resolve scopes for a module.
 * For newly create modules, resolving may happen before module files are
 * created, see b/120797515.
 */
class AndroidKotlinResolveScopeEnlarger : KotlinResolveScopeEnlarger() {

  companion object {
    private val LOG = Logger.getInstance(AndroidResolveScopeEnlarger::class.java)!!
  }

  override fun getAdditionalResolveScope(module: Module, includeTestScope: Boolean): SearchScope? {
    val lightClasses = findAdditionalClassesForModule(module, includeTestScope) ?: return null
    LOG.debug { "Enlarging scope for $module with ${lightClasses.size} light Android classes." }
    return getResolveScopeForAdditionalClasses(lightClasses, module.project)
  }
}