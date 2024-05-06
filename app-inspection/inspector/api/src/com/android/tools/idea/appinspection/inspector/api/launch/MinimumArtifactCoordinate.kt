/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspector.api.launch

enum class MinimumArtifactCoordinate(module: Module, override val version: String) :
  ArtifactCoordinate {
  COMPOSE_UI(Module.COMPOSE_UI, "1.0.0-beta02"),
  COMPOSE_UI_ANDROID(Module.COMPOSE_UI_ANDROID, "1.5.0-beta01"),
  WORK_RUNTIME(Module.WORK_RUNTIME, "2.5.0"),
  ;

  override val groupId = module.groupId
  override val artifactId = module.artifactId

  fun toWild() = RunningArtifactCoordinate(this, "+")

  override fun toString() = toCoordinateString()

  private enum class Module(val groupId: String, val artifactId: String) {
    COMPOSE_UI("androidx.compose.ui", "ui"),
    COMPOSE_UI_ANDROID("androidx.compose.ui", "ui-android"),
    WORK_RUNTIME("androidx.work", "work-runtime"),
  }
}
