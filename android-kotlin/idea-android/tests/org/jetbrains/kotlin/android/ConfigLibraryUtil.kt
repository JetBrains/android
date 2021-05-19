/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File

// Adapted from the Kotlin test framework (after taking over android-kotlin sources).
/**
 * Helper for configuring kotlin runtime in tested project.
 */
object ConfigLibraryUtil {
  private const val DEFAULT_JAVA_RUNTIME_LIB_NAME = "JAVA_RUNTIME_LIB_NAME"
  private const val DEFAULT_KOTLIN_TEST_LIB_NAME = "KOTLIN_TEST_LIB_NAME"

  private fun getKotlinRuntimeLibEditor(libName: String, library: File): NewLibraryEditor {
    val editor = NewLibraryEditor()
    editor.name = libName
    editor.addRoot(VfsUtil.getUrlForLibraryRoot(library), OrderRootType.CLASSES)

    return editor
  }

  fun configureKotlinRuntime(module: Module) {
    addLibrary(getKotlinRuntimeLibEditor(DEFAULT_JAVA_RUNTIME_LIB_NAME, PathUtil.kotlinPathsForDistDirectory.stdlibPath), module)
    addLibrary(getKotlinRuntimeLibEditor(DEFAULT_KOTLIN_TEST_LIB_NAME, PathUtil.kotlinPathsForDistDirectory.kotlinTestPath), module)
  }

  fun unConfigureKotlinRuntime(module: Module) {
    removeLibrary(module, DEFAULT_JAVA_RUNTIME_LIB_NAME)
    removeLibrary(module, DEFAULT_KOTLIN_TEST_LIB_NAME)
  }

  private fun addLibrary(editor: NewLibraryEditor, module: Module, kind: PersistentLibraryKind<*>? = null): Library =
    runWriteAction {
      val rootManager = ModuleRootManager.getInstance(module)
      val model = rootManager.modifiableModel

      val library = addLibrary(editor, model, kind)

      model.commit()

      library
    }

  fun addLibrary(editor: NewLibraryEditor, model: ModifiableRootModel, kind: PersistentLibraryKind<*>? = null): Library {
    val libraryTableModifiableModel = model.moduleLibraryTable.modifiableModel
    val library = libraryTableModifiableModel.createLibrary(editor.name, kind)

    val libModel = library.modifiableModel
    editor.applyTo(libModel as LibraryEx.ModifiableModelEx)

    libModel.commit()
    libraryTableModifiableModel.commit()

    return library
  }


  fun removeLibrary(module: Module, libraryName: String): Boolean {
    return runWriteAction {
      var removed = false

      val rootManager = ModuleRootManager.getInstance(module)
      val model = rootManager.modifiableModel

      for (orderEntry in model.orderEntries) {
        if (orderEntry is LibraryOrderEntry) {

          val library = orderEntry.library
          if (library != null) {
            val name = library.name
            if (name != null && name == libraryName) {

              // Dispose attached roots
              val modifiableModel = library.modifiableModel
              for (rootUrl in library.rootProvider.getUrls(OrderRootType.CLASSES)) {
                modifiableModel.removeRoot(rootUrl, OrderRootType.CLASSES)
              }
              for (rootUrl in library.rootProvider.getUrls(OrderRootType.SOURCES)) {
                modifiableModel.removeRoot(rootUrl, OrderRootType.SOURCES)
              }
              modifiableModel.commit()

              model.moduleLibraryTable.removeLibrary(library)

              removed = true
              break
            }
          }
        }
      }

      model.commit()

      removed
    }
  }

  fun addLibrary(module: Module, libraryName: String, rootPath: String?, jarPaths: Array<String>) {
    val editor = NewLibraryEditor()
    editor.name = libraryName
    for (jarPath in jarPaths) {
      val jarFile = rootPath?.let {
        File(rootPath, jarPath).takeIf { it.exists() }
        ?: FileUtil.findFilesByMask(jarPath.toPattern(), File(rootPath)).firstOrNull()
      } ?: File(jarPath)

      require(jarFile.exists()) {
        "Cannot configure library with given path, file doesn't exists $jarPath"
      }
      editor.addRoot(VfsUtil.getUrlForLibraryRoot(jarFile), OrderRootType.CLASSES)
    }

    addLibrary(editor, module)
  }
}
