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
@file:JvmName("ApkFacetChecker")
package com.android.tools.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * Helper methods for checking for presence of ApkFacet that don't require a dependency on
 * the intellij.android.core module.
 */
fun Module.hasApkFacet(): Boolean =
  ApplicationManager.getApplication().getUserData(APK_FACET_CHECKER_KEY)?.hasApkFacet(this) ?: false

fun Project.hasApkFacet(): Boolean =
  ApplicationManager.getApplication().getUserData(APK_FACET_CHECKER_KEY)?.hasApkFacet(this) ?: false

fun initializeApkFacetChecker(checker: ApkFacetCheckerInternal) {
  ApplicationManager.getApplication().putUserData(APK_FACET_CHECKER_KEY, checker)
}

interface ApkFacetCheckerInternal {
  fun hasApkFacet(module: Module): Boolean
  fun hasApkFacet(project: Project): Boolean
}

private val APK_FACET_CHECKER_KEY: Key<ApkFacetCheckerInternal> = Key.create(ApkFacetCheckerInternal::class.java.name)
