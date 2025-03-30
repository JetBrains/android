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
@file:JvmName("AndroidEnvironmentUtils")
package com.android.tools.idea

import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

/**
 * Returns true if called in Android Studio or if the project has an Android facet.
 */
fun isAndroidEnvironment(project: Project): Boolean =
  IdeInfo.getInstance().isAndroidStudio || CommonAndroidUtil.getInstance().isAndroidProject(project)

/**
 * Checks if the project contains a module with an Android facet.
 */
fun Project.hasAndroidFacet(): Boolean =
  ProjectFacetManager.getInstance(this).hasFacets(AndroidFacet.ID)