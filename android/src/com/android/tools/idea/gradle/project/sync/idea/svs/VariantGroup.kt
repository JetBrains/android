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
package com.android.tools.idea.gradle.project.sync.idea.svs

import com.android.builder.model.NativeVariantAbi
import com.android.builder.model.Variant
import com.android.tools.idea.gradle.project.sync.idea.UsedInBuildAction
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

/**
 * Container used to group multiple [Variant]s and [NativeVariantAbi]s for one module so they can all be registered with
 * the external system [ProjectImportModelProvider.BuildModelConsumer] in [AndroidExtraModelProvider].
 */
@UsedInBuildAction
class VariantGroup(
  val variants: MutableList<Variant> = mutableListOf(),
  val nativeVariants: MutableList<NativeVariantAbi> = mutableListOf()
) : java.io.Serializable
