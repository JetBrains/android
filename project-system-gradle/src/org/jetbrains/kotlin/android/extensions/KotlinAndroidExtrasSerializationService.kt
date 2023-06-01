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
package org.jetbrains.kotlin.android.extensions

import com.android.kotlin.multiplatform.ide.models.serialization.AndroidCompilationModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.AndroidDependencyModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.AndroidSourceSetModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.AndroidTargetModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.androidCompilationKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidSourceSetKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtensionBuilder
import org.jetbrains.kotlin.idea.gradle.configuration.serialize.KotlinExtrasSerializationService

class KotlinAndroidExtrasSerializationService: KotlinExtrasSerializationService {

  override fun IdeaKotlinExtrasSerializationExtensionBuilder.extensions() {
    register(androidTargetKey, AndroidTargetModelSerializer)
    register(androidCompilationKey, AndroidCompilationModelSerializer)
    register(androidSourceSetKey, AndroidSourceSetModelSerializer)
    register(androidDependencyKey, AndroidDependencyModelSerializer)
  }
}