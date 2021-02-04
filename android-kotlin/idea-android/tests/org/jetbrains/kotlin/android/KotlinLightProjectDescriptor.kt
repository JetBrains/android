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

import com.android.testutils.TestUtils
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.android.ConfigLibraryUtil
import java.io.File

// Adapted from the Kotlin test framework (after taking over android-kotlin sources).
open class KotlinLightProjectDescriptor : LightProjectDescriptor() {

  companion object {
    private const val LIBRARY_NAME = "myLibrary"
    private val KOTLIN_PLUGIN = TestUtils.getWorkspaceFile("prebuilts/tools/common/kotlin-plugin/Kotlin")
    private val KOTLIN_STDLIB = File(KOTLIN_PLUGIN, "kotlinc/lib/kotlin-stdlib.jar")
    val INSTANCE = KotlinLightProjectDescriptor()
  }

  override fun getModuleTypeId(): String = ModuleTypeId.JAVA_MODULE

  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    val editor = NewLibraryEditor()
    editor.name = LIBRARY_NAME
    editor.addRoot(VfsUtil.getUrlForLibraryRoot(KOTLIN_STDLIB), OrderRootType.CLASSES)
    ConfigLibraryUtil.addLibrary(editor, model)
  }
}
