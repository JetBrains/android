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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.JavaCompileOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldJavaCompileOptions

open class LegacyJavaCompileOptions(private val javaCompileOptions: JavaCompileOptions) : OldJavaCompileOptions {
  override fun getEncoding(): String = javaCompileOptions.encoding
  override fun getSourceCompatibility(): String = javaCompileOptions.sourceCompatibility
  override fun getTargetCompatibility(): String = javaCompileOptions.targetCompatibility

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "encoding=$encoding," +
                                    "sourceCompatibility=$sourceCompatibility," +
                                    "targetCompatibility=$targetCompatibility" +
                                    "}"
}
