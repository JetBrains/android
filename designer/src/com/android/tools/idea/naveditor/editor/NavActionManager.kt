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
package com.android.tools.idea.naveditor.editor

import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ResourceUtil
import icons.AndroidIcons
import org.jetbrains.android.AndroidGotoRelatedProvider
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.resourceManagers.LocalResourceManager
import java.awt.Image
import java.awt.Toolkit
import java.util.*
import javax.swing.JComponent

/**
 * Provides and handles actions in the navigation editor
 */
class NavActionManager(surface: NavDesignSurface) : ActionManager<NavDesignSurface>(surface) {

  override fun registerActionsShortcuts(component: JComponent) {
    // TODO
  }

  override fun createPopupMenu(actionManager: com.intellij.openapi.actionSystem.ActionManager,
                               leafComponent: NlComponent?): DefaultActionGroup {
    // TODO
    return DefaultActionGroup()
  }

  override fun addActions(group: DefaultActionGroup,
                          component: NlComponent?,
                          parent: NlComponent?,
                          newSelection: List<NlComponent>,
                          toolbar: Boolean) {
    group.add(CreateDestinationMenu(mySurface))
    group.add(AddExistingDestinationMenu(mySurface, destinations))
  }

  @VisibleForTesting
  val destinations: List<Destination>
    get() {
      val model = mySurface.model!!
      val classToDestination = LinkedHashMap<PsiClass, Destination>()
      val module = model.module
      val schema = mySurface.schema

      val scope = GlobalSearchScope.moduleWithDependenciesScope(module)
      val project = model.project
      val parent = mySurface.currentNavigation
      for (superClassName in NavigationSchema.DESTINATION_SUPERCLASS_TO_TYPE.keys) {
        val psiSuperClass = JavaPsiFacade.getInstance(project).findClass(superClassName, GlobalSearchScope.allScope(project)) ?: continue
        val tag = schema.getTagForComponentSuperclass(superClassName) ?: continue
        val query = ClassInheritorsSearch.search(psiSuperClass, scope, true)
        for (psiClass in query) {
          val destination = Destination.RegularDestination(parent, tag, null, psiClass.name, psiClass.qualifiedName)
          classToDestination.put(psiClass, destination)
        }
      }

      val resourceManager = LocalResourceManager.getInstance(module)
      if (resourceManager != null) {
        for (resourceFile in resourceManager.findResourceFiles(ResourceFolderType.LAYOUT)) {
          if (resourceFile !is XmlFile) {
            continue
          }
          // TODO: refactor AndroidGotoRelatedProvider so this can be done more cleanly
          val itemComputable = AndroidGotoRelatedProvider.getLazyItemsForXmlFile(resourceFile, model.facet)
          for (item in itemComputable?.compute() ?: continue) {
            val element = item.element as? PsiClass ?: continue
            val tag = schema.findTagForComponent(element) ?: continue
            val destination =
                Destination.RegularDestination(parent, tag, null, element.name, element.qualifiedName, layoutFile = resourceFile)
            classToDestination.put(element, destination)
          }
        }
      }
      val result = classToDestination.values.toMutableList()

      for (navPsi in resourceManager!!.findResourceFiles(ResourceFolderType.NAVIGATION)) {
        if (mySurface.model!!.file == navPsi) {
          continue
        }
        if (navPsi is XmlFile) {
          result.add(Destination.IncludeDestination(navPsi.name, parent))
        }
      }

      return result
    }

}

// TODO: replace with an appropriate dynamically-determined icon
@VisibleForTesting
@JvmField
val ACTIVITY_IMAGE: Image = Toolkit.getDefaultToolkit().getImage(
    ResourceUtil.getResource(AndroidIcons::class.java, "/icons/naveditor", "basic-activity.png"))
