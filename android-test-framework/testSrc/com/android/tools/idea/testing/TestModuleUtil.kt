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

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.junit.AssumptionViolatedException

fun Project.findAppModule(): Module = findModule("app")

fun Project.findModule(name: String): Module = runReadAction {
  ModuleManager.getInstance(this).findModuleByName(name)
} ?: throw AssumptionViolatedException("Unable to find module with name '$name'")

fun Project.hasModule(name: String): Boolean = runReadAction {
  ModuleManager.getInstance(this).findModuleByName(name)
} != null