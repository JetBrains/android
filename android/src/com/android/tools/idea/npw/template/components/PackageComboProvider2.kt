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
package com.android.tools.idea.npw.template.components

import com.android.tools.idea.observable.AbstractProperty
import com.android.tools.idea.observable.ui.TextProperty
import com.android.tools.idea.wizard.template.Parameter
import com.android.tools.idea.wizard.template.StringParameter
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCodeFragment
import com.intellij.ui.EditorComboBox
import com.intellij.ui.JavaReferenceEditorUtil
import com.intellij.ui.RecentsManager

/**
 * Provides an editable combobox which allows the user to specify a package name (or pull from a list of recently used packages).
 */
class PackageComboProvider2(private val project: Project,
                            parameter: StringParameter,
                            private val initialPackage: String,
                            private val recentsKey: String) : ParameterComponentProvider2<EditorComboBox>(parameter) {
  override fun createComponent(parameter: Parameter<*>): EditorComboBox {
    val doc = JavaReferenceEditorUtil.createDocument(
      initialPackage, project, false, JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE)!!
    val classComboBox = EditorComboBox(doc, project, StdFileTypes.JAVA)

    // Make sure our suggested package is in the recents list and at the top
    RecentsManager.getInstance(project).registerRecentEntry(recentsKey, initialPackage)
    val recents = RecentsManager.getInstance(project).getRecentEntries(recentsKey)!!
    // We just added at least one entry!

    classComboBox.setHistory(recents.toTypedArray())
    return classComboBox
  }

  override fun createProperty(component: EditorComboBox): AbstractProperty<*>? = TextProperty(component)

  override fun accept(component: EditorComboBox) {
    RecentsManager.getInstance(project).registerRecentEntry(recentsKey, component.text)
  }
}
