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

package org.jetbrains.kotlin.android

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.utils.KotlinPaths

// Adapted from the Kotlin test framework (after taking over android-kotlin sources).
open class KotlinLightProjectDescriptor : LightProjectDescriptor() {

  companion object {
    val INSTANCE = KotlinLightProjectDescriptor()
  }

  override fun getModuleTypeId(): String = ModuleTypeId.JAVA_MODULE

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    val editor = NewLibraryEditor()
    editor.name = "kotlin-stdlib"
    val stdlibUrl = VfsUtil.getUrlForLibraryRoot(ConfigLibraryUtil.kotlinPaths.jar(KotlinPaths.Jar.StdLib))
    editor.addRoot(stdlibUrl, OrderRootType.CLASSES)
    ConfigLibraryUtil.addLibrary(editor, model)
  }
}
