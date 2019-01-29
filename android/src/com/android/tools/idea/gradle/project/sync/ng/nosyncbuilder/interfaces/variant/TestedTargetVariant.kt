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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto

/**
 * Class representing the tested variants.
 *
 * This is currently used by the test modules, and contains the same pieces of information
 * as the ones used to define the tested application (and it's variant).
 */
interface TestedTargetVariant {
  /** The Gradle path of the project that is being tested. */
  val targetProjectPath: String

  /** The variant of the tested project. */
  val targetVariant: String

  fun toProto() = VariantProto.TestedTargetVariant.newBuilder()
    .setTargetProjectPath(targetProjectPath)
    .setTargetVariant(targetVariant)
    .build()!!
}

