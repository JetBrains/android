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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl.COMPILE_SDK_VERSION
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SdkOrPreviewTransform
import com.android.tools.idea.gradle.dsl.parser.android.CompileSdkBlockDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression

class CompileSdkPropertyModelImpl(private val internalModel: ResolvedPropertyModel) : CompileSdkPropertyModel,
                                                                                      ResolvedPropertyModel by internalModel {
  companion object {

    private val ADDON_PATTERN = "([^:]+):([^:]+):(\\d+)".toRegex()
    private val API_PATTERN = "android-(\\d+)(?:\\.(?<minor>\\d+))?(-ext(?<ext>\\d+))?".toRegex()


    @JvmStatic
    fun getOrCreateCompileSdkPropertyModel(parent: GradlePropertiesDslElement, maybeCreateAfter: ResolvedPropertyModel? = null): CompileSdkPropertyModelImpl {
      val context = parent.dslFile.context
      val compileSdkBlockVersion = VersionConstraint.agpFrom(COMPILE_SDK_BLOCK_VERSION)

      val createInPosition = maybeCreateAfter?.let {
        parent.children.indexOf(it.rawElement).takeIf { it >= 0 }
      }?.plus(1)

      val compileSdkBlock = parent.getPropertyElement(
        CompileSdkBlockDslElement.COMPILE_SDK
      )

      // DSL element already exists
      parent.getPropertyElement(COMPILE_SDK_VERSION)?.let{
        return CompileSdkPropertyModelImpl(createOldResolvedPropertyModel(parent, createInPosition))
      }

      if (compileSdkBlock != null) {
        return CompileSdkPropertyModelImpl(CompileSdkBlockPropertyModel(compileSdkBlock))
      }

      // agpVersion is null for oldDsl tests
      if (context.agpVersion != null && compileSdkBlockVersion.isOkWith(context.agpVersion)) {
        // new DSL is possible
        val newCompileSdkBlock = if (createInPosition == null) {
          parent.ensurePropertyElement(CompileSdkBlockDslElement.COMPILE_SDK)
        }
        else {
          parent.ensurePropertyElementAt(CompileSdkBlockDslElement.COMPILE_SDK, createInPosition)
        }
        return CompileSdkPropertyModelImpl(CompileSdkBlockPropertyModel(newCompileSdkBlock))
      }
      else {
        // AGP is old need to stick to old DSL
        return CompileSdkPropertyModelImpl(createOldResolvedPropertyModel(parent, createInPosition))
      }
    }

    private fun createOldResolvedPropertyModel(
      parent: GradlePropertiesDslElement,
      createInPosition: Int?
    ): ResolvedPropertyModel =
      GradlePropertyModelBuilder.create(parent, COMPILE_SDK_VERSION).addTransform(
        SdkOrPreviewTransform(
          COMPILE_SDK_VERSION,
          "compileSdkVersion",
          "compileSdk",
          "compileSdkPreview",
          VersionConstraint.agpFrom(COMPILE_SDK_INTRODUCED_VERSION),
          createInPosition
        )
      ).buildResolved()

    fun ResolvedPropertyModel.asCompileSdkString(): String? {
      return when (valueType) {
          ValueType.STRING -> getValue(GradlePropertyModel.STRING_TYPE)
          ValueType.INTEGER -> getValue(GradlePropertyModel.INTEGER_TYPE)?.toString()
          ValueType.CUSTOM -> getValue(GradlePropertyModel.INTEGER_TYPE)?.toString() ?: getValue(GradlePropertyModel.STRING_TYPE)
          else -> null
        }
      }
    }

  class CompileSdkBlockPropertyModel(val dslElement: CompileSdkBlockDslElement) : GradlePropertyModelImpl(
    dslElement
  ), ResolvedPropertyModel {
    val sdkBlockModel = CompileSdkBlockModelImpl(dslElement)
    override fun getValueType(): ValueType = ValueType.CUSTOM
    override fun getResultModel(): GradlePropertyModel = PropertyUtil.resolveModel(this)
  }

  override fun toCompileSdkConfig(): CompileSdkBlockModel? {
    if (internalModel is CompileSdkBlockPropertyModel) {
      return internalModel.sdkBlockModel
    }
    return null
  }

  override fun setValue(value: Any) {
    if (internalModel is CompileSdkBlockPropertyModel) {
      val compileSdkBlock = toCompileSdkConfig()!!
      when (value) {
        is ReferenceTo -> {
          // handle only integer as for release and preview for string
          val referredElement = value.referredElement
          if(referredElement is GradleDslSimpleExpression) {
            when (referredElement.value) {
              is Integer -> compileSdkBlock.setReleaseVersion(value)
              else -> compileSdkBlock.setPreviewVersion(value)
            }
          } else {
            compileSdkBlock.setPreviewVersion(value)
          }
        }
        is Int -> compileSdkBlock.setReleaseVersion(value, null, null)
        else -> {
          val stringValue = value.toString()
          val apiMatchResult = API_PATTERN.matchEntire(stringValue)
          if (apiMatchResult != null) {
            val releaseVersion = apiMatchResult.groupValues[1].toInt()
            val minorApi = apiMatchResult.groups["minor"]?.value?.toInt()
            val extension = apiMatchResult.groups["ext"]?.value?.toInt()
            compileSdkBlock.setReleaseVersion(releaseVersion, minorApi, extension)
            return
          }

          val addonMatchResult = ADDON_PATTERN.matchEntire(stringValue)
          if (addonMatchResult != null) {
            val vendorName = addonMatchResult.groupValues[1]
            val addonName = addonMatchResult.groupValues[2]
            val apiLevel = addonMatchResult.groupValues[3].toInt()
            compileSdkBlock.setAddon(vendorName, addonName, apiLevel)
            return
          }

          // random string
          compileSdkBlock.setPreviewVersion(stringValue)
        }
      }
    }
    else internalModel.setValue(value)
  }

  override fun <T : Any?> getValue(typeReference: TypeReference<T>): T? {
    (internalModel as? CompileSdkBlockPropertyModel)?.let {
      when (typeReference) {
        GradlePropertyModel.STRING_TYPE -> return it.sdkBlockModel.getVersion()?.toHash() as T?
        GradlePropertyModel.INTEGER_TYPE -> return it.sdkBlockModel.getVersion()?.toInt() as T?
        else -> internalModel.getValue(typeReference)
      }
    }
    return internalModel.getValue(typeReference)
  }

  override fun <T: Any> getRawValue(typeReference: TypeReference<T>): T? {
    (internalModel as? CompileSdkBlockPropertyModel)?.let {
      when (typeReference) {
        GradlePropertyModel.STRING_TYPE -> return it.sdkBlockModel.getVersion()?.toHash() as T?
        GradlePropertyModel.OBJECT_TYPE -> return it.sdkBlockModel.getVersion()?.toHash() as T?
        GradlePropertyModel.INTEGER_TYPE -> return it.sdkBlockModel.getVersion()?.toInt() as T?
        else -> internalModel.getRawValue(typeReference)
      }
    }
    return internalModel.getRawValue(typeReference)
  }


  override fun toString(): String {
    return internalModel.toString()
  }

}