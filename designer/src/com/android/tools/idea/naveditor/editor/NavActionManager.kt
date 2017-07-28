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

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.model.NlComponent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtil
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

  override fun registerActions(component: JComponent) {
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
    group.add(AddMenuWrapper(mySurface, destinations))
  }

  @VisibleForTesting
  val destinations: List<Destination>
    get() {
      val model = mySurface.model!!
      val result = LinkedHashMap<PsiClass, Destination>()
      val module = model.module
      val schema = mySurface.schema

      val scope = GlobalSearchScope.moduleWithDependenciesScope(module)
      val project = model.project
      for (superClassName in NavigationSchema.DESTINATION_SUPERCLASS_TO_TYPE.keys) {
        val psiSuperClass = JavaPsiFacade.getInstance(project).findClass(superClassName, GlobalSearchScope.allScope(project)) ?: continue
        val tag = schema.getTagForComponentSuperclass(superClassName) ?: continue
        val query = ClassInheritorsSearch.search(psiSuperClass, scope, true)
        for (psiClass in query) {
          val destination = Destination.create(null, psiClass, tag, ACTIVITY_IMAGE) ?: continue
          result.put(psiClass, destination)
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
            val destination = Destination.create(resourceFile, element, tag, ACTIVITY_IMAGE) ?: continue
            result.put(element, destination)
          }
        }
      }

      return ArrayList(result.values)
    }

  @VisibleForTesting
  data class Destination(val layoutFile: XmlFile?, private val className: String, val qualifiedName: String,
                                            val tag: String, val thumbnail: Image?) {

    val name: String
      get() {
        if (layoutFile != null) {
          return FileUtil.getNameWithoutExtension(layoutFile.name)
        }
        return className
      }

    companion object {
      fun create(layout: XmlFile?, psiClass: PsiClass, tag: String, thumbnail: Image): Destination? {
        val className = psiClass.name ?: return null
        val qualifiedName = psiClass.qualifiedName ?: return null
        return Destination(layout, className, qualifiedName, tag, thumbnail)
      }
    }
  }

  companion object {

    fun addElement(destination: Destination?, surface: NavDesignSurface) {
      object : WriteCommandAction<Unit>(surface.project, "Create Fragment", surface.model!!.file) {
        @Throws(Throwable::class)
        override fun run(result: Result<Unit>) {
          var tagName = surface.schema.getTag(NavigationSchema.DestinationType.FRAGMENT)
          if (destination != null) {
            tagName = destination.tag
          }
          val parent = surface.currentNavigation
          val tag = parent.tag.createChildTag(tagName, null, null, true)
          var idBase = tagName
          if (destination != null) {
            idBase = destination.qualifiedName
          }
          val newComponent = surface.model!!.createComponent(tag, parent, null)
          newComponent.assignId(idBase!!)
          if (destination != null) {
            newComponent.setAttribute(ANDROID_URI, ATTR_NAME, destination.qualifiedName)
            val layout = destination.layoutFile
            if (layout != null) {
              // TODO: do this the right way
              val layoutId = "@" + ResourceType.LAYOUT.getName() + "/" + FileUtil.getNameWithoutExtension(layout.name)
              newComponent.setAttribute(TOOLS_URI, ATTR_LAYOUT, layoutId)
            }
          }
        }
      }.execute()
    }
  }
}

// TODO: replace with an appropriate dynamically-determined icon
@VisibleForTesting
@JvmField
val ACTIVITY_IMAGE: Image = Toolkit.getDefaultToolkit().getImage(
    ResourceUtil.getResource(AndroidIcons::class.java, "/icons/naveditor", "basic-activity.png"))
