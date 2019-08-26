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
package com.android.tools.idea.room.migrations

import com.android.tools.idea.room.migrations.generators.JavaMigrationClassGenerator
import com.android.tools.idea.room.migrations.json.SchemaBundle
import com.android.tools.idea.room.migrations.update.DatabaseUpdate
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.JavaProjectRootsUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import org.jetbrains.jps.model.java.JavaSourceRootType

class GenerateRoomMigrationAction : AnAction("Generate a Room migration") {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    if (files.size == 2) {
      files.sortBy { it.nameWithoutExtension }
      val module = ModuleUtilCore.findModuleForFile(files[0], project) ?: return
      val targetPackage = getDefaultTargetPackage(project, module, files[0]) ?: return
      val targetDirectory = getDefaultTargetDirectory(project, module) ?: return
      val migrationDialog = GenerateMigrationDialog(project, targetPackage, targetDirectory)

      if (!ApplicationManager.getApplication().isUnitTestMode) {
        migrationDialog.show()
        if (!migrationDialog.isOK) {
          return
        }
      }

      val oldSchema = SchemaBundle.deserialize(files[0].inputStream)
      val newSchema = SchemaBundle.deserialize(files[1].inputStream)
      WriteCommandAction.runWriteCommandAction(project) {
        try {
          val javaMigrationClassGenerator = JavaMigrationClassGenerator(project)
          javaMigrationClassGenerator.createMigrationClass(migrationDialog.targetDirectory,
                                                           migrationDialog.targetPackage,
                                                           DatabaseUpdate(oldSchema.database, newSchema.database))
        } catch (e : Exception) {
          Messages.showInfoMessage(project, e.message, "Failed to generate a migration")
        }
      }
    }
  }

  private fun getDefaultTargetDirectory(project: Project, module: Module): PsiDirectory? {
    return module.rootManager.contentEntries.asSequence()
      .flatMap { it.getSourceFolders(JavaSourceRootType.SOURCE).asSequence() }
      .filterNot { JavaProjectRootsUtil.isForGeneratedSources(it) }
      .mapNotNull { it.file }
      .firstOrNull()
      ?.let { PsiManager.getInstance(project).findDirectory(it) }
  }

  private fun getDefaultTargetPackage(project: Project, module: Module, schemaJsonFile: VirtualFile): PsiPackage? {
    if (!schemaJsonFile.parent.isDirectory) {
      return null
    }

    // The schema JSON files seem to always be generated in a directory/package which is named after the fully qualified name of the class
    // that describes the database.
    val databaseClass = JavaPsiFacade.getInstance(project).findClass(schemaJsonFile.parent.name,
                                                                     module.getModuleWithDependenciesAndLibrariesScope(false))
                        ?: return null

    val packageName = databaseClass.qualifiedName?.let { StringUtil.getPackageName(it) } ?: return null

    return JavaPsiFacade.getInstance(project).findPackage(packageName)
  }
}