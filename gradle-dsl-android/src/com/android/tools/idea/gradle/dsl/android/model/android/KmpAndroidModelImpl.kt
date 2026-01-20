/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.android.model.android

import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkPropertyModel
import com.android.tools.idea.gradle.dsl.android.api.android.KmpAndroidModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SdkOrPreviewTransform
import com.android.tools.idea.gradle.dsl.android.parser.android.KmpAndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint

class KmpAndroidModelImpl(dslElement: KmpAndroidDslElement):
  KmpAndroidModel, GradleDslBlockModel(dslElement) {
  override fun namespace(): ResolvedPropertyModel {
    return getModelForProperty(AndroidModelImpl.NAMESPACE)
  }

  override fun compileSdkVersion(): CompileSdkPropertyModel {
    return CompileSdkPropertyModelImpl.getOrCreateCompileSdkPropertyModel(
      myDslElement, null)
  }

  override fun compileSdkVersion(maybeCreateAfter: ResolvedPropertyModel?): CompileSdkPropertyModel {
    return CompileSdkPropertyModelImpl.getOrCreateCompileSdkPropertyModel(
      myDslElement, maybeCreateAfter)
  }

  override fun compileSdkMinor(): ResolvedPropertyModel {
    return getModelForProperty(AndroidModelImpl.COMPILE_SDK_MINOR)
  }

  override fun compileSdkExtension(): ResolvedPropertyModel {
    return getModelForProperty(AndroidModelImpl.COMPILE_SDK_EXTENSION)
  }

  override fun minSdkVersion(): ResolvedPropertyModel {
    val agp410plus = VersionConstraint.agpFrom("4.1.0")
    return GradlePropertyModelBuilder.create(myDslElement, ProductFlavorModelImpl.MIN_SDK_VERSION)
      .addTransform(SdkOrPreviewTransform(
        ProductFlavorModelImpl.MIN_SDK_VERSION, "minSdkVersion", "minSdk", "minSdkPreview", agp410plus))
      .buildResolved()
  }
}