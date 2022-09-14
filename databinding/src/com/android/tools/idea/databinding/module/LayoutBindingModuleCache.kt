/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding.module

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.databinding.BindingLayout
import com.android.tools.idea.databinding.BindingLayoutGroup
import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.databinding.DataBindingModeTrackingService
import com.android.tools.idea.databinding.ViewBindingEnabledTrackingService
import com.android.tools.idea.databinding.index.BindingLayoutType
import com.android.tools.idea.databinding.psiclass.BindingClassConfig
import com.android.tools.idea.databinding.psiclass.BindingImplClassConfig
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.psiclass.LightBrClass
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.util.dependsOn
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import net.jcip.annotations.GuardedBy
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.facet.AndroidFacet

private val LIGHT_BINDING_CLASSES_KEY = Key.create<List<LightBindingClass>>("LIGHT_BINDING_CLASSES_KEY")

@ThreadSafe
class LayoutBindingModuleCache(private val module: Module) {
  companion object {
    // We are using facet.mainModule as a temporary workaround. This is needed because main, unitTest and androidTest modules
    // all access the same resources (all the resources). Ideally, they should only access their own resources.
    @JvmStatic
    fun getInstance(facet: AndroidFacet) = facet.mainModule.getService(LayoutBindingModuleCache::class.java)!!
  }

  private val lock = Any()

  @GuardedBy("lock")
  private var _dataBindingMode = DataBindingMode.NONE
  var dataBindingMode: DataBindingMode
    get() = synchronized(lock) {
      return _dataBindingMode
    }
    set(value) {
      synchronized(lock) {
        if (_dataBindingMode != value) {
            _dataBindingMode = value
            DataBindingModeTrackingService.getInstance().incrementModificationCount()
        }
      }
    }

  @GuardedBy("lock")
  private var _viewBindingEnabled = false
  private var viewBindingEnabled: Boolean
    get() = synchronized(lock) {
      return _viewBindingEnabled
    }
    set(value) {
      synchronized(lock) {
        if (_viewBindingEnabled != value) {
          _viewBindingEnabled = value
          ViewBindingEnabledTrackingService.instance.incrementModificationCount()
        }
      }
    }

  init {
    fun syncModeWithDependencies() {
      dataBindingMode = determineDataBindingMode(module)
      AndroidFacet.getInstance(module)?.let { facet ->
        viewBindingEnabled = facet.isViewBindingEnabled()
      }
    }

    module.project.messageBus.connect().subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object : ProjectSystemSyncManager.SyncResultListener {
      override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
        syncModeWithDependencies()
      }
    })
    syncModeWithDependencies()
  }

  private fun determineDataBindingMode(module: Module): DataBindingMode {
    return when {
      module.dependsOn(GoogleMavenArtifactId.ANDROIDX_DATA_BINDING_LIB) -> DataBindingMode.ANDROIDX
      module.dependsOn(GoogleMavenArtifactId.DATA_BINDING_LIB) -> DataBindingMode.SUPPORT
      else -> DataBindingMode.NONE
    }
  }

  @GuardedBy("lock")
  private var _lightBrClass: LightBrClass? = null
  /**
   * Fetches the singleton light BR class associated with this module.
   *
   * If this is the first time requesting this information, it will be created on the fly.
   *
   * This can return `null` if the current module is not associated with an
   * [AndroidFacet] OR if we were not able to obtain enough information from the given facet
   * at this time (e.g. because we couldn't determine the class's fully-qualified name).
   */
  val lightBrClass: LightBrClass?
    get() {
      val facet = AndroidFacet.getInstance(module) ?: return null

      synchronized(lock) {
        if (_lightBrClass == null) {
          val qualifiedName = DataBindingUtil.getBrQualifiedName(facet) ?: return null
          _lightBrClass = LightBrClass(PsiManager.getInstance(facet.module.project), facet, qualifiedName)
        }
        return _lightBrClass
      }
    }


  @GuardedBy("lock")
  private var _lightDataBindingComponentClass: LightDataBindingComponentClass? = null
  /**
   * Fetches the singleton light DataBindingComponent class associated with this module.
   *
   * If this is the first time requesting this information, it will be created on the fly.
   *
   * This can return `null` if the current module is not associated with an
   * [AndroidFacet] OR if the current module doesn't provide one (e.g. it's not an app
   * module).
   */
  val lightDataBindingComponentClass: LightDataBindingComponentClass?
    get() {
      val facet = AndroidFacet.getInstance(module)?.takeUnless { it.configuration.isLibraryProject } ?: return null

      synchronized(lock) {
        if (_lightDataBindingComponentClass == null) {
          _lightDataBindingComponentClass = LightDataBindingComponentClass(PsiManager.getInstance(module.project), facet)
        }
        return _lightDataBindingComponentClass
      }
    }

  private val BindingLayoutGroup.layoutFileName: String
    get() = mainLayout.file.name

  /**
   * A modification tracker for module resources.
   *
   * We keep track of it to know when to regenerate [BindingLayoutGroup]s, since they depend on
   * resources. See also [bindingLayoutGroups].
   */
  @GuardedBy("lock")
  private var lastResourcesModificationCount = Long.MIN_VALUE

  @GuardedBy("lock")
  private var _bindingLayoutGroups = emptySet<BindingLayoutGroup>()
  /**
   * Returns all [BindingLayoutGroup] instances associated with this module, representing all layouts
   * that should have bindings generated for them.
   *
   * See also [getLightBindingClasses].
   */
  val bindingLayoutGroups: Collection<BindingLayoutGroup>
    get() {
      val facet = AndroidFacet.getInstance(module) ?: return emptySet()

      // This method is designed to occur only within a read action, so we know that dumb mode
      // won't change on us in the middle of it.
      ApplicationManager.getApplication().assertReadAccessAllowed()

      // If we're called at a time before indexes are ready, BindingLayout.tryCreate below would
      // fail with an exception. To prevent this, we abort early with what we have.
      if (DumbService.isDumb(module.project)) {
        Logger.getInstance(LayoutBindingModuleCache::class.java).info(
          "Binding classes may be temporarily stale due to indices not being accessible right now.")
        return _bindingLayoutGroups
      }

      synchronized(lock) {
        val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
        val modificationCount = moduleResources.modificationCount
        if (modificationCount != lastResourcesModificationCount) {
          val layoutResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT)
          _bindingLayoutGroups = layoutResources.values()
            .mapNotNull { resource -> BindingLayout.tryCreate(facet, resource) }
            .groupBy { info -> info.file.name }
            .map { entry -> BindingLayoutGroup(entry.value) }
            .toSet()
          lastResourcesModificationCount = modificationCount
        }

        return _bindingLayoutGroups
      }
    }

  /**
   * Returns a list of [LightBindingClass] instances corresponding to the layout XML files
   * related to the passed-in [BindingLayoutGroup].
   *
   * If there is only one layout.xml (i.e. single configuration), this will return a single light
   * class (a "Binding"). If there are multiple layout.xmls (i.e. multi- configuration), this will
   * return a main light class ("Binding") as well as several additional implementation light
   * classes ("BindingImpl"s), one for each layout.
   *
   * If this is the first time requesting this information, they will be created on the fly.
   *
   * @param group A group that you can get by calling [bindingLayoutGroups]
   */
  fun getLightBindingClasses(group: BindingLayoutGroup): List<LightBindingClass> {
    val facet = AndroidFacet.getInstance(module) ?: return emptyList()

    synchronized(lock) {
      var bindingClasses = group.getUserData(LIGHT_BINDING_CLASSES_KEY)
      if (bindingClasses == null) {
        bindingClasses = ArrayList()

        // Always add a full "Binding" class.
        val psiManager = PsiManager.getInstance(module.project)
        val bindingClass = LightBindingClass(psiManager, BindingClassConfig(facet, group))
        bindingClasses.add(bindingClass)

        // "Impl" classes are only necessary if we have more than a single configuration.
        // Also, only create "Impl" bindings for data binding; view binding does not generate them
        if (group.layouts.size > 1 && group.mainLayout.data.layoutType == BindingLayoutType.DATA_BINDING_LAYOUT) {
          for (layoutIndex in group.layouts.indices) {
            val bindingImplClass = LightBindingClass(psiManager, BindingImplClassConfig(facet, group, layoutIndex))
            bindingClasses.add(bindingImplClass)
          }
        }

        group.putUserData(LIGHT_BINDING_CLASSES_KEY, bindingClasses)
      }
      return bindingClasses
    }
  }
}
