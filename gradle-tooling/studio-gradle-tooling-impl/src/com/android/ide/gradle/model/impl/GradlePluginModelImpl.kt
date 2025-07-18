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

package com.android.ide.gradle.model.impl

import com.android.ide.gradle.model.GradlePluginModel
import java.io.Serializable

/**
 * Implementation of the [GradlePluginModel] model object.
 */
data class GradlePluginModelImpl(
  private val hasSafeArgsJava: Boolean,
  private val hasSafeArgsKotlin: Boolean,
  private val hasKotlinMultiPlatform: Boolean
) : GradlePluginModel, Serializable {
  override fun hasSafeArgsJava(): Boolean = hasSafeArgsJava
  override fun hasSafeArgsKotlin(): Boolean = hasSafeArgsKotlin
  override fun hasKotlinMultiPlatform(): Boolean = hasKotlinMultiPlatform

  companion object {
    private const val serialVersionUID = 4L
  }
}
