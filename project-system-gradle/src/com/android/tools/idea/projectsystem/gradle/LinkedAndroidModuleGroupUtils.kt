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

import com.android.tools.idea.util.androidFacet
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.android.facet.AndroidFacet

/**
 * This class, along with the key [LINKED_ANDROID_GRADLE_MODULE_GROUP] is used to track and group modules
 * that are based on the same Gradle project.  In Gradle projects, instances of this class will be attached
 * to all Android modules.  This class should not be accessed directly from outside the Gradle project system.
 */
data class LinkedAndroidGradleModuleGroup(
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

/**
 * Key used to store [LinkedAndroidGradleModuleGroup] on all modules that are part of the same Gradle project.  This key should
 * not be accessed from outside the Gradle project system.
 */
val LINKED_ANDROID_GRADLE_MODULE_GROUP = Key.create<LinkedAndroidGradleModuleGroup>("linked.android.gradle.module.group")

fun Module.isHolderModule() : Boolean = getHolderModule() == this
fun Module.getHolderModule() : Module = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.holder ?: this
fun Module.isMainModule() : Boolean = getMainModule() == this
fun Module.getMainModule() : Module = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.main ?: this
fun Module.isUnitTestModule() : Boolean = getUnitTestModule() == this
fun Module.getUnitTestModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.unitTest
fun Module.isAndroidTestModule() : Boolean = getAndroidTestModule() == this
fun Module.getAndroidTestModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.androidTest
fun Module.isScreenshotTestModule() : Boolean = getScreenshotTestModule() == this
fun Module.getScreenshotTestModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.screenshotTest
fun Module.isTestFixturesModule() : Boolean = getTestFixturesModule() == this
fun Module.getTestFixturesModule() : Module? = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.testFixtures

/**
 * Utility method to find out if a module is derived from an Android Gradle project. This will return true
 * if the given module is the module representing any of the Android source sets (main/unitTest/androidTest/screenshotTest/testFixtures)
 * or the holder module used as the parent of these source set modules.
 */
fun Module.isLinkedAndroidModule() = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP) != null
fun Module.getAllLinkedModules() : List<Module> = getUserData(LINKED_ANDROID_GRADLE_MODULE_GROUP)?.getModules() ?: listOf(this)

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