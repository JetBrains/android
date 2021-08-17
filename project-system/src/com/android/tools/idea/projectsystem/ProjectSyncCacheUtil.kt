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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Caches a value until the next sync happens.
 */
fun <T> Project.cacheInvalidatingOnSyncModifications(provider: () -> T): T {
  return cacheByClassInvalidatingOnSyncModifications(provider::class.java, provider)
}

/**
 * Utility method to cache an element provided via [provider] and for the given [dependencies].
 * This provides the same functionality as the method with the same name provided in Kotlin plugin in cacheUtil.kt
 */
private fun <T> Project.cacheByClass(classForKey: Class<*>, vararg dependencies: Any, provider: () -> T): T {
  return CachedValuesManager.getManager(this).cache(this, dependencies, classForKey, provider)
}

/**
 * Utility method to cache an element provided via [provider] that will be invalidated on sync.
 * This provides the equivalent functionality to cacheByClassInvalidatingOnRootModifications in the Kotlin plugin in cacheUtil.kt
 */
private fun <T> Project.cacheByClassInvalidatingOnSyncModifications(classForKey: Class<*>, provider: () -> T): T {
  return cacheByClass(classForKey,
                      ProjectSyncModificationTracker.getInstance(this),
                      provider = provider)
}

private fun <T> CachedValuesManager.cache(
  holder: UserDataHolder,
  dependencies: Array<out Any>,
  classForKey: Class<*>,
  provider: () -> T
): T {
  return getCachedValue(
    holder,
    getKeyForClass(classForKey),
    { CachedValueProvider.Result.create(provider(), dependencies) },
    false
  )
}
