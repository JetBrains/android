/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.model

import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil

/**
 * Project gradle root representation model
 * @param name The gradle root name
 * @param ideaGradleJdk The jdk.table.xml entry name or macro defined on [ExternalSystemJdkUtil] used to configure the gradle java for sync
 * @param localPropertiesJdkPath The jdk.dir property located on local.properties per gradle root
 * @param modulesPath A list containing the gradle root modules absolute path
 */
data class GradleRoot(
  val name: String = "",
  val ideaGradleJdk: String? = null,
  val localPropertiesJdkPath: String? = null,
  val modulesPath: List<String> = listOf()
)