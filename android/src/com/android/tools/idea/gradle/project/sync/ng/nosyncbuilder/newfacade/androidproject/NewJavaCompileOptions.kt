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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject.JavaCompileOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldJavaCompileOptions
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto

data class NewJavaCompileOptions(
  override val encoding: String,
  override val sourceCompatibility: String,
  override val targetCompatibility: String
) : JavaCompileOptions {
  constructor(oldJavaCompileOptions: OldJavaCompileOptions) : this(
    oldJavaCompileOptions.encoding,
    oldJavaCompileOptions.sourceCompatibility,
    oldJavaCompileOptions.targetCompatibility
  )

  constructor(proto: AndroidProjectProto.JavaCompileOptions) : this(
    proto.encoding,
    proto.sourceCompatibility,
    proto.targetCompatibility
  )
}
