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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.androidproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.AndroidProjectProto

/** Java compile options. */
interface JavaCompileOptions {
  /** The java compiler encoding setting. */
  val encoding: String

  /** The level of compliance Java source code has. */
  val sourceCompatibility: String

  /** The Java version to be able to run classes on. */
  val targetCompatibility: String

  fun toProto() = AndroidProjectProto.JavaCompileOptions.newBuilder()
    .setEncoding(encoding)
    .setSourceCompatibility(sourceCompatibility)
    .setTargetCompatibility(targetCompatibility)
    .build()!!
}
