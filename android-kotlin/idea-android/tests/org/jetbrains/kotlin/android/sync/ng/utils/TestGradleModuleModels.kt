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
package org.jetbrains.kotlin.android.sync.ng.utils

import com.android.tools.idea.gradle.project.sync.GradleModuleModels
import org.junit.Assert.assertTrue

class TestGradleModuleModels(private val _moduleName: String) : GradleModuleModels {
  private val modelMap = mutableMapOf<Class<*>, Any?>()

  fun addModel(modelType: Class<*>, model: Any?) {
    assertTrue("Passed in object must be an instance of the given type.", modelType.isInstance(model))
    modelMap[modelType] = model
  }

  override fun <T : Any?> findModels(modelType: Class<T>): MutableList<T> = mutableListOf(modelMap[modelType]).filterIsInstance(
    modelType).toMutableList()

  override fun getModuleName(): String = _moduleName

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> findModel(modelType: Class<T>): T? = modelMap[modelType] as T
}