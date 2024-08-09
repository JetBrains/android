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
import com.android.tools.idea.databinding.index.BindingXmlIndexModificationTracker
import com.android.tools.idea.databinding.psiclass.BindingClassConfig
import com.android.tools.idea.databinding.psiclass.BindingImplClassConfig
import com.android.tools.idea.databinding.psiclass.EagerLightBindingClassConfig
import com.android.tools.idea.databinding.psiclass.LightBindingClass
import com.android.tools.idea.databinding.psiclass.LightBrClass
import com.android.tools.idea.databinding.psiclass.LightDataBindingComponentClass
import com.android.tools.idea.databinding.util.DataBindingUtil
import com.android.tools.idea.databinding.util.isViewBindingEnabled
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import net.jcip.annotations.ThreadSafe
import org.jetbrains.android.augment.AndroidLightClassBase
import org.jetbrains.android.facet.AndroidFacet

/**
 * Key used to mark the [VirtualFile]s backing any light classes created in this cache, so that they
 * can be recognized elsewhere and included in the search scope when necessary.
 */
private val BACKING_FILE_MARKER: Key<Any> = Key("LIGHT_BINDING_CLASS_BACKING_FILE_MARKER")

@ThreadSafe
class LayoutBindingModuleCache(val module: Module) : Disposable {
  companion object {
    // We are using facet.mainModule as a temporary workaround. This is needed because main,
    // unitTest and androidTest modules all access the same resources (all the resources). Ideally,
    // they should only access their own resources.
    @JvmStatic
    fun getInstance(facet: AndroidFacet): LayoutBindingModuleCache =
      facet.module.getMainModule().service()
  }

  /** Value to be stored with [BACKING_FILE_MARKER], unique to this module. */
  private val moduleBindingClassMarker = Any()

  /**
   * Search scope which includes any light binding classes generated in this cache for the current
   * module.
   */
  val lightBindingClassSearchScope: GlobalSearchScope =
    LightBindingClassSearchScope(moduleBindingClassMarker)

  private val _dataBindingMode = AtomicReference(DataBindingMode.NONE)
  var dataBindingMode: DataBindingMode
    get() = _dataBindingMode.get()
    set(value) {
      val oldValue = _dataBindingMode.getAndSet(value)
      if (oldValue != value) {
        DataBindingModeTrackingService.getInstance().incModificationCount()
      }
    }

  private val _viewBindingEnabled = AtomicBoolean(false)
  private var viewBindingEnabled: Boolean
    get() = _viewBindingEnabled.get()
    set(value) {
      val oldValue = _viewBindingEnabled.getAndSet(value)
      if (oldValue != value) {
        ViewBindingEnabledTrackingService.getInstance().incModificationCount()
      }
    }

  init {
    fun syncModeWithDependencies() {
      AndroidFacet.getInstance(module).let { facet ->
        dataBindingMode = determineDataBindingMode(facet)
        viewBindingEnabled = facet?.isViewBindingEnabled() ?: false
      }
    }

    module.project.messageBus
      .connect(this)
      .subscribe(
        PROJECT_SYSTEM_SYNC_TOPIC,
        ProjectSystemSyncManager.SyncResultListener { syncModeWithDependencies() },
      )
    syncModeWithDependencies()
  }

  private fun determineDataBindingMode(facet: AndroidFacet?): DataBindingMode {
    val moduleSystem = facet?.module?.getModuleSystem()
    return when {
      moduleSystem == null || !moduleSystem.isDataBindingEnabled -> DataBindingMode.NONE
      moduleSystem.useAndroidX -> DataBindingMode.ANDROIDX
      else -> DataBindingMode.SUPPORT
    }
  }

  private val _lightBrClass = AtomicReference<LightBrClass?>()
  /**
   * Fetches the singleton light BR class associated with this module.
   *
   * If this is the first time requesting this information, it will be created on the fly.
   *
   * This can return `null` if the current module is not associated with an [AndroidFacet] OR if we
   * were not able to obtain enough information from the given facet at this time (e.g. because we
   * couldn't determine the class's fully-qualified name).
   */
  val lightBrClass: LightBrClass?
    get() =
      _lightBrClass.updateAndGet { clazz ->
        val facet = AndroidFacet.getInstance(module) ?: return@updateAndGet null

        // Reuse the existing class if it's already been created.
        if (clazz != null) return@updateAndGet clazz

        val qualifiedName = DataBindingUtil.getBrQualifiedName(facet) ?: return@updateAndGet null
        LightBrClass(PsiManager.getInstance(facet.module.project), facet, qualifiedName)
          .withMarkedBackingFile()
      }

  private val _lightDataBindingComponentClass = AtomicReference<LightDataBindingComponentClass?>()
  /**
   * Fetches the singleton light DataBindingComponent class associated with this module.
   *
   * If this is the first time requesting this information, it will be created on the fly.
   *
   * This can return `null` if the current module is not associated with an [AndroidFacet] OR if the
   * current module doesn't provide one (e.g. it's not an app module).
   */
  val lightDataBindingComponentClass: LightDataBindingComponentClass?
    get() =
      _lightDataBindingComponentClass.updateAndGet { clazz ->
        val facet =
          AndroidFacet.getInstance(module)?.takeUnless { it.configuration.isLibraryProject }
            ?: return@updateAndGet null

        // Reuse the existing class if it's already been created.
        if (clazz != null) return@updateAndGet clazz
        LightDataBindingComponentClass(PsiManager.getInstance(module.project), facet)
          .withMarkedBackingFile()
      }

  /**
   * Returns all [BindingLayoutGroup] instances associated with this module, representing all
   * layouts that should have bindings generated for them.
   *
   * See also [getLightBindingClasses].
   */
  val bindingLayoutGroups: Collection<BindingLayoutGroup>
    get() = getLayoutGroupsAndLightClasses().keys

  private fun getLayoutGroupsAndLightClasses(): Map<BindingLayoutGroup, List<LightBindingClass>> {
    val facet = AndroidFacet.getInstance(module) ?: return emptyMap()

    // This method is designed to occur only within a read action, so we know that dumb mode
    // won't change on us in the middle of it.
    ApplicationManager.getApplication().assertReadAccessAllowed()

    val project = module.project
    return CachedValuesManager.getManager(project).getCachedValue(facet) {
      val moduleResources = StudioResourceRepositoryManager.getModuleResources(facet)
      val modificationTracker = ModificationTracker { moduleResources.modificationCount }
      val layoutResources =
        moduleResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.LAYOUT)
      val bindingLayoutGroups =
        layoutResources
          .values()
          .mapNotNull { resource -> BindingLayout.tryCreate(facet, resource) }
          .groupBy { info -> info.file.name }
          .map { entry -> BindingLayoutGroup(entry.value) }
          .toSet()

      val groupsWithClasses =
        bindingLayoutGroups.associateWith { createLightBindingClasses(facet, it) }

      // Note: LocalResourceRepository and BindingXmlIndex are updated at different times,
      // so we must incorporate both into the modification count (see b/283753328).
      CachedValueProvider.Result(
        groupsWithClasses,
        modificationTracker,
        BindingXmlIndexModificationTracker.getInstance(project),
      )
    }
  }

  /**
   * Returns a list of [LightBindingClass] instances corresponding to the layout XML files
   * associated with this facet.
   *
   * If there is only one layout.xml for a given group (i.e. single configuration), this will return
   * a single light class for that group (a "Binding"). If there are multiple layout.xmls (i.e.
   * multi- configuration), this will return a main light class ("Binding") as well as several
   * additional implementation light classes ("BindingImpl"s) for the group, one for each layout.
   *
   * The groupFilter function is used to filter the [BindingLayoutGroup]s that correspond to the
   * light classes; only classes for the filtered groups will be returned.
   */
  fun getLightBindingClasses(
    groupFilter: ((BindingLayoutGroup) -> Boolean)? = null
  ): List<LightBindingClass> {
    val groupsAndClasses = getLayoutGroupsAndLightClasses()
    val filteredGroupsAndClasses =
      if (groupFilter != null) groupsAndClasses.filterKeys(groupFilter) else groupsAndClasses

    return filteredGroupsAndClasses.values.flatten()
  }

  private fun createLightBindingClasses(
    facet: AndroidFacet,
    group: BindingLayoutGroup,
  ): List<LightBindingClass> {
    val configs = buildList {
      // Always add a full "Binding" class.
      add(BindingClassConfig(facet, group))

      // "Impl" classes are only necessary if we have more than a single configuration.
      // Also, only create "Impl" bindings for data binding; view binding does not generate them
      if (
        group.layouts.size > 1 &&
          group.mainLayout.data.layoutType == BindingLayoutType.DATA_BINDING_LAYOUT
      ) {
        for (layoutIndex in group.layouts.indices) {
          add(BindingImplClassConfig(facet, group, layoutIndex))
        }
      }
    }

    // If we are evaluating config when it's constructed, wrap the above config objects in an
    // implementation that will eagerly evaluate their data now.
    val wrappedConfigs =
      if (StudioFlags.EVALUATE_BINDING_CONFIG_AT_CONSTRUCTION.get())
        configs.map(::EagerLightBindingClassConfig)
      else configs

    val psiManager = PsiManager.getInstance(facet.module.project)
    return wrappedConfigs.map { LightBindingClass(psiManager, it).withMarkedBackingFile() }
  }

  override fun dispose() {}

  private fun <T : AndroidLightClassBase> T.withMarkedBackingFile() = apply {
    requireNotNull(containingFile)
      .viewProvider
      .virtualFile
      .putUserData(BACKING_FILE_MARKER, moduleBindingClassMarker)
  }

  /** Search scope which recognizes any light classes created with the given marker. */
  private class LightBindingClassSearchScope(private val bindingClassMarker: Any) :
    GlobalSearchScope() {
    override fun contains(file: VirtualFile): Boolean {
      return file.getUserData(BACKING_FILE_MARKER) === bindingClassMarker
    }

    override fun isSearchInModuleContent(aModule: Module) = true

    override fun isSearchInLibraries() = false
  }
}
