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
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement

class CompileSdkBlockModelImpl(dslElement: GradlePropertiesDslElement) : GradleDslBlockModel(dslElement), CompileSdkBlockModel {
  companion object {
    const val VERSION = "mVersion"
    const val RELEASE_NAME = "release"
    const val PREVIEW_NAME = "preview"
    const val ADDON_NAME = "addon"
  }

  override fun getVersion(): CompileSdkVersionModel? {
    val version = myDslElement.getPropertyElement(VERSION)
    if (version is GradleDslMethodCall) {
      return when (version.methodName) {
        RELEASE_NAME -> CompileSdkReleaseModelImpl(version)
        PREVIEW_NAME -> {
          val previewProperty = GradlePropertyModelBuilder.create(myDslElement, VERSION)
            .addTransform(SingleArgumentMethodTransform(PREVIEW_NAME))
            .buildResolved()

          object : CompileSdkPreviewModel {
            override fun toHash(): String? = previewProperty.resolve().valueAsString()
            override fun toInt(): Int? = null
            override fun getVersion(): ResolvedPropertyModel = previewProperty
            override fun delete() = previewProperty.delete()
          }
        }
        ADDON_NAME -> CompileSdkAddonModelImpl(version)
        else -> null
      }
    }
    return null
  }

  override fun setReleaseVersion(version: Int, minorApi: Int?, extension: Int?) {
    myDslElement.removeProperty(VERSION)
    val name = GradleNameElement.create(VERSION)
    val methodCall = GradleDslMethodCall(myDslElement, name, RELEASE_NAME)
    myDslElement.setNewElement(methodCall)
    val versionLiteral = GradleDslLiteral(methodCall.argumentsElement, GradleNameElement.empty())
    versionLiteral.setValue(version)
    methodCall.addNewArgument(versionLiteral)

    if (minorApi != null || extension != null) {
      val closure = GradleDslClosure(methodCall, null, GradleNameElement.create(RELEASE_NAME))
      methodCall.setNewClosureElement(closure)

      minorApi?.let {
        closure.setNewElement(createAssignment(closure, it, "minorApiLevel"))
      }
      extension?.let {
        closure.setNewElement(createAssignment(closure, it, "sdkExtension"))
      }
    }
  }

  private fun createAssignment(parent: GradleDslElement, value: Int, name: String): GradleDslSimpleExpression {
    val newElement = GradleDslLiteral(parent, GradleNameElement.create(name))
    newElement.setValue(value)
    newElement.elementType = PropertyType.REGULAR
    newElement.externalSyntax = ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT
    return newElement
  }

  override fun setPreviewVersion(version: String) {
    myDslElement.removeProperty(VERSION)
    val methodCall = GradleDslMethodCall(myDslElement, GradleNameElement.create(VERSION), PREVIEW_NAME)
    myDslElement.setNewElement(methodCall)
    val versionLiteral = GradleDslLiteral(methodCall.argumentsElement, GradleNameElement.empty())
    versionLiteral.setValue(version)
    methodCall.addNewArgument(versionLiteral)
  }

  override fun setAddon(vendorName: String, addonName: String, apiLevel: Int) {
    myDslElement.removeProperty(VERSION)
    val methodCall = GradleDslMethodCall(myDslElement, GradleNameElement.create(VERSION), ADDON_NAME)
    myDslElement.setNewElement(methodCall)
    addArgument(methodCall, vendorName)
    addArgument(methodCall, addonName)
    addArgument(methodCall, apiLevel)
  }

  private fun addArgument(methodCall: GradleDslMethodCall, value: Any){
    val versionLiteral = GradleDslLiteral(methodCall.argumentsElement, GradleNameElement.empty())
    versionLiteral.setValue(value)
    methodCall.addNewArgument(versionLiteral)
  }

}