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
package com.android.tools.idea.util

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.util.xml.DomElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.facet.implementingModules

val Module.androidFacet: AndroidFacet? get() = AndroidFacet.getInstance(this)
val PsiElement.androidFacet: AndroidFacet? get() = AndroidFacet.getInstance(this)
val DomElement.androidFacet: AndroidFacet? get() = AndroidFacet.getInstance(this)

/**
 * Checks if this module has Android facet.
 */
fun Module.isAndroidModule(): Boolean = AndroidFacet.getInstance(this) != null

/**
 * Find the corresponding Android module for this one.
 * If this is already an Android module - returns itself.
 * If this is a common module - it will return an implementing Android module (if any).
 */
fun Module.findAndroidModule(): Module? {
  return if (isAndroidModule()) this else implementingModules.firstOrNull { it.isAndroidModule() }
}

/**
 * Checks if this module is "common", and one of its implementations is Android.
 */
fun Module.isCommonWithAndroidModule(): Boolean =
  implementingModules.isNotEmpty() && implementingModules.any { it.isAndroidModule() }
