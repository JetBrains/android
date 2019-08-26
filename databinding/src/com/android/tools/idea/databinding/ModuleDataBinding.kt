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
package com.android.tools.idea.databinding

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.databinding.psiclass.BindingClassConfig
import com.android.tools.idea.databinding.psiclass.BindingImplClassConfig
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.psiclass.LightBrClass
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerAdapter
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.xml.XmlFile
import net.jcip.annotations.GuardedBy
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.facet.AndroidFacet
import java.util.ArrayList

@ThreadSafe
class ModuleDataBinding private constructor(private val module: Module) {
  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): ModuleDataBinding {
      // service registered in android plugin
      return ModuleServiceManager.getService(facet.module, ModuleDataBinding::class.java)!!
    }
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

  /**
   * A backing cache of binding groups, which will get recalculated whenever any of their grouped
   * layout files change.
   */
  @GuardedBy("lock")
  private val myBindingLayoutGroupsCache: CachedValue<Collection<BindingLayoutGroup>>

  /**
   * A key used for storing / retrieving a cache of light binding classes.
   *
   * Light binding classes should get automatically collected whenever their backing layout file
   * is deleted. Therefore, we store them on the layout file's [XmlFile], tying their lifetime
   * to it.
   */
  @GuardedBy("lock")
  private val lightBindingClassesKey: Key<List<LightBindingClass>> = Key.create("ModuleDataBinding.LightBindingClasses")

  init {
    fun syncModeWithFacetConfiguration() {
      dataBindingMode = AndroidFacet.getInstance(module)?.configuration?.model?.dataBindingMode ?: return
    }

    val connection = module.messageBus.connect(module)
    connection.subscribe(FacetManager.FACETS_TOPIC, object : FacetManagerAdapter() {
      override fun facetConfigurationChanged(facet: Facet<*>) {
        if (facet.module === module) {
          syncModeWithFacetConfiguration()
        }
      }
    })
    syncModeWithFacetConfiguration()

    /**
     * Generates all [BindingLayoutGroup]s for the current module.
     */
    fun generateGroups(facet: AndroidFacet, moduleResources: LocalResourceRepository): Collection<BindingLayoutGroup> {
      val modulePackage = MergedManifestManager.getSnapshot(facet).getPackage() ?: return emptySet()
      val layoutResources = moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT)
      return layoutResources.values()
        .map { resource -> BindingLayout(facet, modulePackage, resource) }
        .groupBy { info -> info.file.name }
        .map { entry -> BindingLayoutGroup(entry.value) }
    }

    synchronized(lock) {
      val facet = AndroidFacet.getInstance(module)
      val cachedValuesManager = CachedValuesManager.getManager(module.project)
      if (facet != null) {
        myBindingLayoutGroupsCache = cachedValuesManager.createCachedValue {
          val moduleResources = ResourceRepositoryManager.getModuleResources(facet)
          val groups = generateGroups(facet, moduleResources)
          CachedValueProvider.Result.create(groups, moduleResources)
        }
      }
      else {
        myBindingLayoutGroupsCache = cachedValuesManager.createCachedValue {
          CachedValueProvider.Result.create(emptyList<BindingLayoutGroup>() as Collection<BindingLayoutGroup>)
        }
      }
    }
  }

  /**
   * Returns all [BindingLayoutGroup] instances associated with this module, representing all layouts
   * that should have bindings generated for them.
   *
   * See also [getLightBindingClasses].
   */
  val bindingLayoutGroups: Collection<BindingLayoutGroup>
    get() {
      synchronized(lock) {
        return myBindingLayoutGroupsCache.value
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
   * This information is cached against the PSI of the main layout of the passed-in group, so if
   * the layout is ever deleted, the associated light binding classes will eventually get released.
   */
  fun getLightBindingClasses(group: BindingLayoutGroup): List<LightBindingClass> {
    val facet = AndroidFacet.getInstance(module) ?: return emptyList()

    synchronized(lock) {
      val layoutXmlFile = group.mainLayout.toXmlFile()
      var bindingClasses = layoutXmlFile.getUserData(lightBindingClassesKey)
      if (bindingClasses != null) {
        // If here, we have a previously cached set of binding classes. However, a layout
        // configuration (e.g. layout-land) may have been added or removed, at which point, we
        // need to ignore the cache. The logic here is in sync with the logic in the next block
        // -- that is, if there's only one layout, we generate a Binding, but if there's multiple
        // layouts, we generate a general Binding plus one BindingImpl per layout.
        val numLayouts = group.layouts.size
        val numClassesToGenerate = if (numLayouts == 1) 1 else numLayouts + 1
        if (bindingClasses.size != numClassesToGenerate) {
          bindingClasses = null
        }
      }

      if (bindingClasses == null) {
        bindingClasses = ArrayList()

        // Always add a full "Binding" class.
        val psiManager = PsiManager.getInstance(module.project)
        val bindingClass = LightBindingClass(psiManager, BindingClassConfig(facet, group))
        bindingClasses.add(bindingClass)

        // "Impl" classes are only necessary if we have more than a single configuration.
        if (group.layouts.size > 1) {
          for (layoutIndex in 0 until group.layouts.size) {
            val layout = group.layouts[layoutIndex]
            val bindingImplClass = LightBindingClass(psiManager, BindingImplClassConfig(facet, group, layoutIndex))
            layout.psiClass = bindingImplClass
            bindingClasses.add(bindingImplClass)
          }
        }
        else {
          group.mainLayout.psiClass = bindingClass
        }

        layoutXmlFile.putUserData(lightBindingClassesKey, bindingClasses)
      }
      return bindingClasses
    }
  }
}
