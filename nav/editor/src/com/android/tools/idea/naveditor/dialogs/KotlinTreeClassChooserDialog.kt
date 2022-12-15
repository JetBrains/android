/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs

import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.TreeClassChooser
import com.intellij.ide.util.TreeJavaClassChooserDialog
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.projectView.KtClassOrObjectTreeNode
import org.jetbrains.kotlin.psi.KtClassOrObject
import javax.swing.tree.DefaultMutableTreeNode


interface KotlinTreeClassChooserFactory {
  fun createKotlinTreeClassChooser(title: String,
                                   project: Project,
                                   scope: GlobalSearchScope,
                                   base: PsiClass?,
                                   initialClass: PsiClass?,
                                   classFilter: ClassFilter): TreeClassChooser

  companion object {
    private val instance = object : KotlinTreeClassChooserFactory {
      override fun createKotlinTreeClassChooser(title: String,
                                                project: Project,
                                                scope: GlobalSearchScope,
                                                base: PsiClass?,
                                                initialClass: PsiClass?,
                                                classFilter: ClassFilter): TreeClassChooser =
        KotlinTreeClassChooserDialog(title, project, scope, base, initialClass, classFilter)
    }

    fun getInstance(): KotlinTreeClassChooserFactory = instance
  }
}

/**
 * This is an implementation of [TreeJavaClassChooserDialog] with support for Kotlin classes.
 *
 * The class works around https://youtrack.jetbrains.com/issue/KTIJ-7948.
 */
private class KotlinTreeClassChooserDialog(title: String,
                                           project: Project,
                                           scope: GlobalSearchScope,
                                           private val base: PsiClass?,
                                           initialClass: PsiClass?,
                                           classFilter: ClassFilter)
  : TreeJavaClassChooserDialog(title, project, scope, classFilter, base, initialClass, false) {
  override fun getSelectedFromTreeUserObject(node: DefaultMutableTreeNode?): PsiClass? {
    val psiClass = super.getSelectedFromTreeUserObject(node)
                   ?: node!!.userObject?.let { userObject ->
                     if (userObject !is KtClassOrObjectTreeNode) null else (userObject.value as KtClassOrObject).toLightClass()
                   }

    val isAccepted = psiClass?.let {
      it.isInheritor(base ?: return@let false, true) && filter.isAccepted(psiClass)
    } ?: false

    return if (isAccepted) psiClass else null
  }
}