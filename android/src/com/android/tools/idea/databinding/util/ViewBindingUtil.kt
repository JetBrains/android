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
@file:JvmName("ViewBindingUtil")

package com.android.tools.idea.databinding.util

import com.android.tools.idea.databinding.LayoutBindingSupport
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.isAndroidx

fun Project.getViewBindingClassName(): String {
  return if (isAndroidx()) "androidx.viewbinding.ViewBinding" else "android.viewbinding.ViewBinding"
}

fun AndroidFacet.isViewBindingEnabled() = getModuleSystem().isViewBindingEnabled

// Note: We don't really need the "Project" here but it keeps the function from being globally
// scoped and also indicates that the tracker is associated with a project and not a module.
fun Project.getViewBindingEnabledTracker(): ModificationTracker {
  return LayoutBindingSupport.EP_NAME.extensionList.firstOrNull()?.viewBindingEnabledTracker
    ?: ModificationTracker { 0L }
}
