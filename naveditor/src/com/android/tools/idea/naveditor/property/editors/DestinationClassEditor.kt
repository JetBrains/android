/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.editors

import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.util.ClassUtil
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.isInProject
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.annotations.TestOnly

// TODO: ideally this wouldn't be a separate editor, and EnumEditor could just get the EnumSupport from the property itself.
class DestinationClassEditor(listener: NlEditingListener = Listener, comboBox: CustomComboBox = CustomComboBox(),
                             @TestOnly private val inProject: (PsiClass) -> Boolean = { it.isInProject() })
  : EnumEditor(listener, comboBox, null, true, true) {

  @VisibleForTesting
  object Listener : NlEditingListener {
    override fun stopEditing(editor: NlComponentEditor, value: Any?) {
      NlWriteCommandActionUtil.run(editor.property.components[0], "Set Class Name") layout@{
        DEFAULT_LISTENER.stopEditing(editor, value)
        val className = value as? String ?: return@layout
        editor.property.components[0].setAttribute(TOOLS_URI, ATTR_LAYOUT, (editor as DestinationClassEditor).findLayoutForClass(className))
      }
    }

    override fun cancelEditing(editor: NlComponentEditor) {}
  }

  // TODO: support multiple layouts per class
  private fun findLayoutForClass(className: String): String? {
    val module = property.model.module
    val resourceManager = LocalResourceManager.getInstance(module) ?: return null

    for (resourceFile in resourceManager.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.LAYOUT)
      .filterIsInstance<XmlFile>()) {
      val contextClass = AndroidPsiUtils.getContextClass(module, resourceFile) ?: continue
      if (contextClass.qualifiedName == className) {
        return "@layout/" + FileUtil.getNameWithoutExtension(resourceFile.name)
      }
    }

    return null
  }

  override fun getEnumSupport(property: NlProperty): EnumSupport = SubclassEnumSupport(property, inProject)

  private class SubclassEnumSupport(property: NlProperty, private val inProject: (PsiClass) -> Boolean) : EnumSupport(property) {
    override fun getAllValues(): List<ValueWithDisplayString> {
      val component = myProperty.components[0]
      val schema = NavigationSchema.get(component.model.module)

      val classes = schema.getProjectClassesForTag(component.tagName)
        .filter { it.qualifiedName != null }
        .distinctBy { it.qualifiedName }

      val displayItems = classes
        .map { ValueWithDisplayString(displayString(it.qualifiedName!!), it.qualifiedName) to inProject(it) }
        .sortedWith(compareBy({ !it.second }, { it.first.displayString }))

      return displayItems
        .map { it.first }
        .toList()
    }

    override fun createFromResolvedValue(resolvedValue: String, value: String?, hint: String?): ValueWithDisplayString {
      return ValueWithDisplayString(displayString(resolvedValue), value, hint)
    }

    private fun displayString(qName: String): String = "${ClassUtil.extractClassName(qName)} (${ClassUtil.extractPackageName(qName)})"
  }
}