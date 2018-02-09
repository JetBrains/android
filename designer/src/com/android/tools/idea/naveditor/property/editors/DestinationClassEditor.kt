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
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.EnumEditor
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.naveditor.model.destinationType
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener
import com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER
import com.android.tools.idea.uibuilder.property.editors.support.EnumSupport
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.AndroidGotoRelatedProvider
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.resourceManagers.LocalResourceManager

// TODO: ideally this wouldn't be a separate editor, and EnumEditor could just get the EnumSupport from the property itself.
class DestinationClassEditor(listener: NlEditingListener, comboBox: CustomComboBox) : EnumEditor(listener, comboBox, null, true, true) {

  constructor() : this(Listener, CustomComboBox())

  @VisibleForTesting
  object Listener: NlEditingListener {
    override fun stopEditing(editor: NlComponentEditor, value: Any?) {
      NlWriteCommandAction.run(editor.property.components[0], "Set Class Name") layout@{
        DEFAULT_LISTENER.stopEditing(editor, value)
        val className = value as? String ?: return@layout
        editor.property.components[0].setAttribute(TOOLS_URI, ATTR_LAYOUT, (editor as DestinationClassEditor).findLayoutForClass(className))
      }
    }

    override fun cancelEditing(editor: NlComponentEditor) {}
  }

  // TODO: support multiple layouts per class
  private fun findLayoutForClass(className: String): String? {
    val resourceManager = LocalResourceManager.getInstance(property.model.module) ?: return null

    for (resourceFile in resourceManager.findResourceFiles(ResourceFolderType.LAYOUT).filterIsInstance<XmlFile>()) {
      // TODO: refactor AndroidGotoRelatedProvider so this can be done more cleanly
      val itemComputable = AndroidGotoRelatedProvider.getLazyItemsForXmlFile(resourceFile, property.model.facet)
      for (item in itemComputable?.compute() ?: continue) {
        val element = item.element as? PsiClass ?: continue
        if (element.qualifiedName == className) {
          return "@layout/" + FileUtil.getNameWithoutExtension(resourceFile.name)
        }
      }
    }
    return null
  }

  override fun getEnumSupport(property: NlProperty): EnumSupport = SubclassEnumSupport(property)

  private class SubclassEnumSupport(property : NlProperty) : EnumSupport(property) {
    override fun getAllValues(): MutableList<ValueWithDisplayString> {
      val component = myProperty.components[0]
      val targetType = component.destinationType
      val project = component.model.project
      val psiFacade = JavaPsiFacade.getInstance(project)
      val allScope = GlobalSearchScope.allScope(project)
      return NavigationSchema.DESTINATION_SUPERCLASS_TO_TYPE
          .filterValues { it == targetType }
          .keys
          .mapNotNull { psiFacade.findClass(it, allScope) }
          .flatMap { ClassInheritorsSearch.search(it, allScope, true) }
          .map { ValueWithDisplayString(it.qualifiedName, it.qualifiedName) }
          .toMutableList()
    }
  }
}