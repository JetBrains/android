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
package org.jetbrains.android.util

import com.intellij.ide.projectView.impl.GroupByTypeComparator
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.util.ClassFilter
import com.intellij.ide.util.TreeJavaClassChooserDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope

object AndroidTreeClassChooserFactory {

  fun createInheritanceClassChooser(
    project: Project,
    title: @NlsContexts.DialogTitle String?,
    scope: GlobalSearchScope?,
    base: PsiClass?,
    initialClass: PsiClass?,
    classFilter: ClassFilter?
  ) = TreeJavaClassChooserDialog(
    title, project, scope, classFilter, GroupByTypeComparator(project, ProjectViewPane.ID), base, initialClass, false
  )

  fun createNoInnerClassesScopeChooser(
    project: Project,
    title: @NlsContexts.DialogTitle String?,
    scope: GlobalSearchScope?,
    classFilter: ClassFilter?,
    initialClass: PsiClass?
  ) = TreeJavaClassChooserDialog(
    title, project, scope, classFilter, GroupByTypeComparator(project, ProjectViewPane.ID), null, initialClass, false
  )
}