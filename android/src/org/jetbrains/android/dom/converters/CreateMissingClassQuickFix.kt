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

import com.android.tools.idea.projectsystem.SourceProviderManager.Companion.getInstance
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPackage
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.Nls

/**
 * A quick fix that creates non-existing class, used, e.g., for invalid android:name references in
 * AndroidManifest.xml file
 */
class CreateMissingClassQuickFix
internal constructor(
  aPackage: PsiPackage,
  private val myClassName: String,
  private val myModule: Module,
  private val myBaseClassFqcn: String?,
) : LocalQuickFix {
  private val myPackage: SmartPsiElementPointer<PsiPackage?>

  /**
   * @param aPackage destination package of a new class
   * @param myClassName created class name
   * @param myModule application module where class should be created
   * @param myBaseClassFqcn if provided, created class would inherit this one
   */
  init {
    myPackage =
      SmartPointerManager.getInstance(aPackage.getProject())
        .createSmartPsiElementPointer<PsiPackage?>(aPackage)
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getName(): @Nls String {
    return String.format("Create class '%s'", myClassName)
  }

  override fun getFamilyName(): @Nls String {
    return "Create class"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val aPackage = myPackage.getElement()
    if (aPackage == null) {
      return
    }

    val facet = AndroidFacet.getInstance(myModule)
    if (facet == null) {
      return
    }

    val sources = getInstance(facet).sources
    val sourceDirs: Iterable<VirtualFile> =
      Iterables.concat<VirtualFile?>(sources.javaDirectories, sources.kotlinDirectories)
    val directories = aPackage.getDirectories()
    val filteredDirectories: MutableList<PsiDirectory?> =
      Lists.newArrayListWithExpectedSize<PsiDirectory?>(directories.size)
    for (directory in directories) {
      for (file in sourceDirs) {
        if (VfsUtilCore.isAncestor(file, directory.getVirtualFile(), true)) {
          filteredDirectories.add(directory)
          break
        }
      }
    }

    val directory: PsiDirectory?
    when (filteredDirectories.size) {
      0 -> directory = null
      1 -> directory = filteredDirectories.get(0)
      else -> {
        // There are several directories, present a dialog window for a user to choose a particular
        // destination directory
        val array: Array<PsiDirectory?> =
          filteredDirectories.toArray<PsiDirectory?>(PsiDirectory.EMPTY_ARRAY)
        directory =
          DirectoryChooserUtil.selectDirectory(
            aPackage.getProject(),
            array,
            filteredDirectories.get(0),
            "",
          )
      }
    }

    if (directory == null) {
      return
    }

    val aClass =
      WriteCommandAction.writeCommandAction(project)
        .compute<PsiClass, RuntimeException?>(
          ThrowableComputable {
            // Create a new class
            val psiClass = JavaDirectoryService.getInstance().createClass(directory, myClassName)

            val facade = JavaPsiFacade.getInstance(project)

            // Add a base class to "extends" list
            val list = psiClass.getExtendsList()
            if (list != null && myBaseClassFqcn != null) {
              val parentClass =
                facade.findClass(myBaseClassFqcn, GlobalSearchScope.allScope(project))
              if (parentClass != null) {
                list.add(facade.getElementFactory().createClassReferenceElement(parentClass))
              }
            }

            // Add a "public" modifier, which is absent by default. Required because classes
            // references in AndroidManifest
            // have to point to public classes.
            val modifierList = psiClass.getModifierList()
            if (modifierList != null) {
              modifierList.setModifierProperty(PsiModifier.PUBLIC, true)
            }
            psiClass
          }
        )

    val fileDescriptor = OpenFileDescriptor(project, aClass.getContainingFile().getVirtualFile())
    FileEditorManager.getInstance(project).openEditor(fileDescriptor, true)
  }
}
