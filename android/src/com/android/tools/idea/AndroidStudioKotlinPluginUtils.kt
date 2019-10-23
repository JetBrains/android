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
@file:JvmName("AndroidStudioKotlinPluginUtils")
package com.android.tools.idea

import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.FacetTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

val kotlinFacetType: FacetType<out Facet<*>, *>? get() = FacetTypeRegistry.getInstance().findFacetType("kotlin-language")

fun Project.hasAnyKotlinModules(): Boolean {
  val kotlinFacetId = kotlinFacetType?.id ?: return false
  return ModuleManager.getInstance(this).modules.any {
    FacetManager.getInstance(it).getFacetByType(kotlinFacetId) != null
  }
}

fun Module.hasKotlinFacet(): Boolean {
  return FacetManager.getInstance(this).getFacetByType((kotlinFacetType ?: return false).id) != null
}

fun isKotlinPluginAvailable(): Boolean {
  return try {
    Class.forName("org.jetbrains.kotlin.idea.facet.KotlinFacet")
    true
  }
  catch (e: ClassNotFoundException) {
    false
  }
  catch (e: LinkageError) {
    false
  }
}
