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

import com.android.tools.idea.databinding.psiclass.BindingClassConfig
import com.android.tools.idea.databinding.psiclass.BindingImplClassConfig
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.psiclass.LightBrClass
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass
import com.android.tools.idea.res.binding.BindingLayoutGroup
import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetManagerAdapter
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.psi.PsiManager
import java.util.ArrayList
import java.util.WeakHashMap
import net.jcip.annotations.GuardedBy
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.facet.AndroidFacet

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
   * A (weak) cache of all generated light binding classes.
   *
   * A binding module should generate one or more binding classes per bindable layout, and we cache
   * everything here. Such a bindable layout should have a [BindingLayoutGroup] associated
   * with it, with the assumption that its own lifetime is tied to the lifetime of the layout
   * itself. For example, if the user deletes a layout file, its binding group should also be
   * released, so in turn these cached binding classes would be collected as well.
   */
  @GuardedBy("lock")
  private val _lightBindingClasses = WeakHashMap<BindingLayoutGroup, MutableList<LightBindingClass>>()
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
   * This information is backed by a weak map, so when the `group` object goes out of scope,
   * the associated light binding classes will eventually get released.
   */
  fun getLightBindingClasses(group: BindingLayoutGroup): List<LightBindingClass> {
    val facet = AndroidFacet.getInstance(module) ?: return emptyList()

    synchronized(lock) {
      var bindingClasses: MutableList<LightBindingClass>? = _lightBindingClasses[group]
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

        _lightBindingClasses[group] = bindingClasses
      }
      return bindingClasses
    }
  }
}
