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

import com.android.tools.idea.gradle.dsl.android.api.android.CompileSdkBetaModel
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement

class CompileSdkBetaModelImpl(private val myMethodCall: GradleDslMethodCall) : CompileSdkBetaModel {
  companion object {
    const val MINOR_API_LEVEL = "minorApiLevel"
    const val BETA_VERSION = "betaVersion"
  }

  private var myClosure: GradleDslClosure = myMethodCall.closureElement ?: GradleDslClosure(myMethodCall, null, GradleNameElement.empty())

  override fun getMinorApiLevel(): ResolvedPropertyModel {
    return GradlePropertyModelBuilder.create(myClosure, MINOR_API_LEVEL).buildResolved()
  }

  override fun getBetaVersion(): ResolvedPropertyModel {
    return GradlePropertyModelBuilder.create(myClosure, BETA_VERSION).buildResolved()
  }

  override fun getVersion(): ResolvedPropertyModel {
    return GradlePropertyModelBuilder.create(myMethodCall.arguments.first()).buildResolved()
  }

  override fun delete() {
    myMethodCall.delete()
  }

  override fun toHash(): String? {
    val apiLevel = getVersion().toInt() ?: return null
    var compileSdkString = "android-$apiLevel"
    val minorApiLevel = getMinorApiLevel().toInt()
    if (minorApiLevel != null) {
      compileSdkString += ".$minorApiLevel"
    }
    val betaVersion = getBetaVersion().toInt()
    if (betaVersion != null) {
      compileSdkString += "-beta$betaVersion"
    }
    return compileSdkString
  }

  override fun toInt(): Int? = null
}
