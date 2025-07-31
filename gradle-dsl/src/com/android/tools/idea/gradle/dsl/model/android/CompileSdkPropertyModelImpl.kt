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
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkPropertyModel
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkPropertyModel.Companion.COMPILE_SDK_BLOCK_VERSION
import com.android.tools.idea.gradle.dsl.api.android.CompileSdkPropertyModel.Companion.COMPILE_SDK_INTRODUCED_VERSION
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SdkOrPreviewTransform
import com.android.tools.idea.gradle.dsl.parser.android.CompileSdkBlockDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint

class CompileSdkPropertyModelImpl(private val internalModel: ResolvedPropertyModel) : CompileSdkPropertyModel, ResolvedPropertyModel by internalModel {
  companion object {
    @JvmStatic
    fun getOrCreateCompileSdkPropertyModel(parent: GradlePropertiesDslElement): CompileSdkPropertyModelImpl {
      val context = parent.dslFile.context
      val agp410plus = VersionConstraint.agpFrom(COMPILE_SDK_INTRODUCED_VERSION)
      val compileSdkBlockVersion = VersionConstraint.agpFrom(COMPILE_SDK_BLOCK_VERSION)
      val compileSdkBlock = parent.getPropertyElement(
        CompileSdkBlockDslElement.COMPILE_SDK)
      val oldModel = GradlePropertyModelBuilder.create(parent, AndroidModelImpl.COMPILE_SDK_VERSION).addTransform(
        SdkOrPreviewTransform(
          AndroidModelImpl.COMPILE_SDK_VERSION, "compileSdkVersion", "compileSdk", "compileSdkPreview", agp410plus
        )
      ).buildResolved()

      // agpVersion is null for oldDsl tests
      if (context.agpVersion != null && compileSdkBlockVersion.isOkWith(context.agpVersion)) {
        // new DSL is possible
        if (compileSdkBlock != null) {
          return CompileSdkPropertyModelImpl(CompileSdkBlockPropertyModel(compileSdkBlock))
        }
        else if (oldModel.psiElement != null) {
          // existing old DSL
          return CompileSdkPropertyModelImpl(oldModel)
        }
        else {
          // brand new element
          val newCompileSdkBlock: CompileSdkBlockDslElement = parent.ensurePropertyElement(
            CompileSdkBlockDslElement.COMPILE_SDK)
          return CompileSdkPropertyModelImpl(CompileSdkBlockPropertyModel(newCompileSdkBlock))
        }
      }
      else {
        // AGP is old need to stick to old DSL
        return CompileSdkPropertyModelImpl(oldModel)
      }
    }

  }

  class CompileSdkBlockPropertyModel(val dslElement: CompileSdkBlockDslElement) : GradlePropertyModelImpl(
    dslElement), ResolvedPropertyModel {
    override fun getValueType(): GradlePropertyModel.ValueType = GradlePropertyModel.ValueType.CUSTOM
    override fun getResultModel(): GradlePropertyModel = PropertyUtil.resolveModel(this)
  }

  override fun toCompileSdkConfig(): CompileSdkBlockModel? {
    if (internalModel is CompileSdkBlockPropertyModel) {
      return CompileSdkBlockModelImpl(internalModel.dslElement)
    }
    return null
  }

  override fun toString(): String {
    return internalModel.toString()
  }

}