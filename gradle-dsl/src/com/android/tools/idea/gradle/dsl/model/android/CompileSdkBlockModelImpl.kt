/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.android

import com.android.tools.idea.gradle.dsl.api.android.CompileSdkBlockModel
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkPreviewModel
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkVersionModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement

class CompileSdkBlockModelImpl(dslElement: GradlePropertiesDslElement) : GradleDslBlockModel(dslElement), CompileSdkBlockModel {
  companion object {
    const val VERSION = "mVersion"
  }

  override fun getVersion(): CompileSdkVersionModel? {
    val version = myDslElement.getPropertyElement(VERSION)
    if (version is GradleDslMethodCall) {
      return if (version.methodName == "release") {
        CompileSdkReleaseModelImpl(version)
      }
      else {
        val previewProperty = GradlePropertyModelBuilder.create(myDslElement, VERSION)
          .addTransform(SingleArgumentMethodTransform("preview"))
          .buildResolved()
        if (previewProperty.psiElement != null) {
          object : CompileSdkPreviewModel {
            override fun getVersion(): ResolvedPropertyModel = previewProperty
          }
        }
        else null
      }
    }
    return null
  }

}