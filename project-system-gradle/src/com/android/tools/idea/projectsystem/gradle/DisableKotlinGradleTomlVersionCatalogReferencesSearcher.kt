/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.navigation.GradleKtsVersionCatalogReferencesSearcher
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * Disable [KotlinGradleTomlVersionCatalogReferencesSearcher] since studio has its own implementation for
 * gradle KTS files using version catalog called [GradleKtsVersionCatalogReferencesSearcher]
 */
class DisableKotlinGradleTomlVersionCatalogReferencesSearcher : ApplicationInitializedListener {
  override suspend fun execute() {
    @Suppress("INVISIBLE_REFERENCE") // KotlinGradleTomlVersionCatalogReferencesSearcher is marked internal in IntelliJ 2025.1+.
    val extension = org.jetbrains.kotlin.idea.gradle.versionCatalog.toml.KotlinGradleTomlVersionCatalogReferencesSearcher::class.java
    ReferencesSearch.EP_NAME.point.unregisterExtension(extension)
  }
}