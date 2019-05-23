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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc

import com.android.tools.idea.templates.TemplateMetadata.*
import kotlin.reflect.full.memberProperties

const val ACTIVITY_TEMPLATE_NAME = "activityTemplateName"
const val MOBILE_PROJECT_NAME = "MobileprojectName"

/**
 * Contains additional information which should be combined with shipped models to create a complete model.
 * It contains things which are set directly by user (e.g. package name) or are environment dependent (e.g. target sdk level).
 */
data class NewProjectExtraInfo(
  val minApi: Int,
  val targetApi: Int,
  val packageName: String,
  val projectLocation: String,
  val sdkDir: String,
  val activityTemplateName: String,
  val projectName: String,
  val mobileProjectName: String
  // TODO(qumeric): extend with wearProjectName etc.
)

// Uses nullable fields instead of lateinit because it is not supported for primitive types.
data class NewProjectExtraInfoBuilder(
  var minApi: Int? = null,
  var targetApi: Int? = null,
  var packageName: String? = null,
  var projectLocation: String? = null,
  var sdkDir: String? = null,
  var activityTemplateName: String? = null,
  var projectName: String? = null,
  var mobileProjectName: String? = null) {

  fun fill(values: Map<String, Any>) {
    minApi = minApi ?: values[ATTR_MIN_API]?.toString()?.toInt()
    targetApi = targetApi ?: values[ATTR_TARGET_API]?.toString()?.toInt()
    packageName = packageName ?: values[ATTR_PACKAGE_NAME]?.toString()
    projectLocation = projectLocation ?: values[ATTR_TOP_OUT]?.toString()
    sdkDir = sdkDir ?: values[ATTR_SDK_DIR]?.toString()
    activityTemplateName = activityTemplateName ?: values[ACTIVITY_TEMPLATE_NAME]?.toString()
    mobileProjectName = mobileProjectName ?: values[MOBILE_PROJECT_NAME]?.toString()

    if (values.containsKey(ATTR_TOP_OUT)) {
      projectName = projectNameFromProjectLocation(values[ATTR_TOP_OUT].toString())
    }
  }

  private fun isFilled(): Boolean = NewProjectExtraInfoBuilder::class.memberProperties.all { it.get(this) != null }

  fun build(): NewProjectExtraInfo {
    if (!isFilled()) {
      val uninitializedFields =  NewProjectExtraInfoBuilder::class.memberProperties.filter { it.get(this) == null }.map { it.name }
      throw IllegalStateException("All fields must be initialized. Currently $uninitializedFields are not.")
    }
    return NewProjectExtraInfo(
      minApi!!,
      targetApi!!,
      packageName!!,
      projectLocation!!,
      sdkDir!!,
      activityTemplateName!!,
      projectName!!,
      mobileProjectName!!
    )
  }
}
