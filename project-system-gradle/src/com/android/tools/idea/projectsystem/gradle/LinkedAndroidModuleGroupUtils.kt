/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.projectsystem.LINKED_ANDROID_GRADLE_MODULE_GROUP
import com.android.tools.idea.projectsystem.isHolderModule
import com.android.tools.idea.util.androidFacet
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet

fun Module.getAllLinkedModules() : List<Module> = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.getModules() ?: listOf(this)
fun Module.isTestFixturesModule() : Boolean = getTestFixturesModule() == this
fun Module.getTestFixturesModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.testFixtures

/**
 * Utility method to find out if a module is derived from an Android Gradle project. This will return true
 * if the given module is the module representing any of the Android source sets (main/unitTest/androidTest/screenshotTest/testFixtures)
 * or the holder module used as the parent of these source set modules.
 */
fun Module.isLinkedAndroidModule() = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP) != null

/** Returns all [AndroidFacet]s on the project. It uses a sequence in order to avoid allocations. */
fun Project.androidFacetsForNonHolderModules(): Sequence<AndroidFacet> {
  return ProjectFacetManager.getInstance(this).getModulesWithFacet(AndroidFacet.ID).asSequence().let {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // We are running some tests that don't set up real-world project structure, so fetch all modules.
      // See http://b/258162266 for more details.
      it
    }
    else {
      // Holder module has associated facet, but it can be ignored.
      it.filter { module -> !module.isHolderModule() }
    }
  }.mapNotNull { it.androidFacet }
}

fun Module.isUnitTestModule() : Boolean = getUnitTestModule() == this
fun Module.getUnitTestModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.unitTest
fun Module.isScreenshotTestModule() : Boolean = getScreenshotTestModule() == this
fun Module.getScreenshotTestModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.screenshotTest