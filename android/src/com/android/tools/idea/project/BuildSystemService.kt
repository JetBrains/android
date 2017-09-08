/*
 * Copyright (C) 2017 The Android Open Source Project
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
@file:JvmName("BuildSystemServiceUtil")

package com.android.tools.idea.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

/**
 * Handles generic build system operations such as syncing and building. Implementations of this interface will
 * receive a {@link Project} instance in their constructor.
 */
interface BuildSystemService {

  /**
   * Returns true iff this object is applicable to the {@link Project} it was created on.
   * This method is called immediately after construction. If it returns false, the instance
   * is discarded and no further methods are invoked.
   */
  fun isApplicable(): Boolean

}

val EP_NAME = ExtensionPointName<BuildSystemService>("com.android.project.buildSystemService")

fun getInstance(project: Project): BuildSystemService? {
  return EP_NAME.getExtensions(project).find { it.isApplicable() }
}