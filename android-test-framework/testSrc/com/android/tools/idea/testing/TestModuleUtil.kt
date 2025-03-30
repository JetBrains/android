/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("TestModuleUtil")

package com.android.tools.idea.testing

import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.junit.AssumptionViolatedException

fun Project.findAppModule(): Module = findModule("app")

/**
 * Attempts to find a module which is represented by the given name [name]. This method first checks to see if qualified names are
 * enabled, if they aren't then we attempt to find an exact match to the given [name]. If they are then we attempt to find a match
 * by prefixing the given [name] with the name of the project.
 *
 * If this yields no match we attempt to find any module which has a [name] as a prefix of the modules name.
 *
 * Tests that rely on multiple modules of the same name (under different parents) should be very careful when calling this method.
 */
fun Project.findModule(name: String) : Module = maybeFindModule(name) ?: throw AssumptionViolatedException(
  "Unable to find module with name '$name', existing modules are ${ModuleManager.getInstance(this).modules.joinToString { it.name }}")

fun Project.hasModule(name: String): Boolean = maybeFindModule(name) != null

private fun Project.maybeFindModule(name: String) : Module? = runReadAction {
  val useQualifiedNames = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(this)?.isUseQualifiedModuleNames ?: false

  val moduleManager = ModuleManager.getInstance(this)
  if (!useQualifiedNames) {
    moduleManager.findModuleByName(name)
  } else {
    moduleManager.findModuleByName("${this.name}.$name")
  } ?: moduleManager.modules.firstOrNull { module -> module.name.endsWith(name) }
}