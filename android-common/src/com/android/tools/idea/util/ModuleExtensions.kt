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
package com.android.tools.idea.util

import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.util.xml.DomElement
import org.jetbrains.android.facet.AndroidFacet

val Module.androidFacet: AndroidFacet? get() = AndroidFacet.getInstance(this)
val PsiElement.androidFacet: AndroidFacet? get() = AndroidFacet.getInstance(this)
val DomElement.androidFacet: AndroidFacet? get() = AndroidFacet.getInstance(this)

/**
 * This class, along with the key [CommonAndroidUtil.LINKED_ANDROID_MODULE_GROUP] is used to track and group modules
 * that are based on the same Gradle project. Instances of this class will be attached to all Android modules.
 *
 * These classes shouldn't be used directly instead utility methods in ModuleUtil in intellij.android.core should be called.
 */
data class LinkedAndroidModuleGroup(
  val holder: Module,
  val main: Module,
  val unitTest: Module?,
  val androidTest: Module?,
  val testFixtures: Module?,
  val screenshotTest: Module?
  ) {
  fun getModules() = listOfNotNull(holder, main, unitTest, androidTest, testFixtures, screenshotTest)
  override fun toString(): String =
    "holder=${holder.name}, main=${main.name}, unitTest=${unitTest?.name}, " +
    "androidTest=${androidTest?.name}, testFixtures=${testFixtures?.name}, screenshotTest=${screenshotTest?.name}"
}