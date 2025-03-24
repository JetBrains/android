/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom.converters

import com.android.tools.idea.projectsystem.SourceProviderManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPackage
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.psi.KtPsiFactory

class CreateMissingJavaClassQuickFix(
  destinationPackage: PsiPackage,
  className: String,
  module: Module,
  baseClassFqName: String?,
) : CreateMissingClassQuickFix(destinationPackage, className, module, baseClassFqName) {

  override fun getName(): @Nls String = "Create Java class '$className'"

  override fun getFamilyName(): @Nls String = "Create Java class"

  override fun createClass(directory: PsiDirectory, project: Project): VirtualFile {
    val psiClass = JavaDirectoryService.getInstance().createClass(directory, className)

    // Add a base class to "extends" list
    if (baseClassFqName != null) {
      psiClass.extendsList?.let { extendsList ->
        val facade = JavaPsiFacade.getInstance(project)
        val parentClass = facade.findClass(baseClassFqName, GlobalSearchScope.allScope(project))
        if (parentClass != null) {
          extendsList.add(facade.elementFactory.createClassReferenceElement(parentClass))
        }
      }
    }

    // Add a "public" modifier, which is absent by default. Required because class references
    // in AndroidManifest have to point to public classes.
    psiClass.modifierList?.setModifierProperty(PsiModifier.PUBLIC, true)

    return psiClass.containingFile.virtualFile
  }
}

class CreateMissingKotlinClassQuickFix(
  destinationPackage: PsiPackage,
  className: String,
  module: Module,
  baseClassFqName: String?,
) : CreateMissingClassQuickFix(destinationPackage, className, module, baseClassFqName) {

  private val packageQualifiedName = destinationPackage.qualifiedName

  override fun getName(): @Nls String = "Create Kotlin class '$className'"

  override fun getFamilyName(): @Nls String = "Create Kotlin class"

  override fun createClass(directory: PsiDirectory, project: Project): VirtualFile {
    val fileName = "$className.kt"
    val fileText = buildString {
      appendLine("package $packageQualifiedName")
      appendLine()

      if (baseClassFqName != null) {
        val baseClassShortName = baseClassFqName.substringAfterLast('.')

        appendLine("import $baseClassFqName")
        appendLine()
        append("class $className : $baseClassShortName()")
      } else {
        append("class $className")
      }

      appendLine(" {\n\n}")
    }

    val file = KtPsiFactory(project).createPhysicalFile(fileName, fileText)
    directory.add(file)

    return file.viewProvider.virtualFile
  }
}

/**
 * A quick fix that creates non-existing class, used, e.g., for invalid android:name references in
 * AndroidManifest.xml file
 */
sealed class CreateMissingClassQuickFix
protected constructor(
  destinationPackage: PsiPackage,
  protected val className: String,
  private val module: Module,
  protected val baseClassFqName: String?,
) : LocalQuickFix {

  private val packagePointer: SmartPsiElementPointer<PsiPackage> =
    SmartPointerManager.getInstance(destinationPackage.project)
      .createSmartPsiElementPointer(destinationPackage)

  override fun startInWriteAction() = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val destinationPackage = packagePointer.getElement() ?: return
    val facet = AndroidFacet.getInstance(module) ?: return

    val sources = SourceProviderManager.getInstance(facet).sources
    val sourceDirs = (sources.javaDirectories + sources.kotlinDirectories).distinct()

    val filteredPackageDirectories =
      destinationPackage.directories.filter { packageDirectory ->
        sourceDirs.any { sourceDir ->
          VfsUtilCore.isAncestor(sourceDir, packageDirectory.virtualFile, true)
        }
      }

    val directory =
      when (filteredPackageDirectories.size) {
        0 -> return
        1 -> filteredPackageDirectories.single()
        else -> {
          // There are several directories, present a dialog window for a user to choose a
          // particular destination directory
          DirectoryChooserUtil.selectDirectory(
            destinationPackage.project,
            filteredPackageDirectories.toTypedArray(),
            filteredPackageDirectories[0],
            "",
          ) ?: return
        }
      }

    val createdFile =
      WriteCommandAction.runWriteCommandAction(
        project,
        Computable { createClass(directory, project) },
      )

    val fileDescriptor = OpenFileDescriptor(project, createdFile)
    FileEditorManager.getInstance(project).openEditor(fileDescriptor, true)
  }

  protected abstract fun createClass(directory: PsiDirectory, project: Project): VirtualFile
}
