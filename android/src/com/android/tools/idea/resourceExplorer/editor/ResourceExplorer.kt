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
package com.android.tools.idea.resourceExplorer.editor

import com.android.tools.idea.resourceExplorer.importer.ImportConfigurationManager
import com.android.tools.idea.resourceExplorer.importer.ImportersProvider
import com.android.tools.idea.resourceExplorer.importer.SynchronizationManager
import com.android.tools.idea.resourceExplorer.view.*
import com.android.tools.idea.resourceExplorer.viewmodel.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.properties.Delegates

private const val withExternalBrowser: Boolean = false
private const val withDetailView: Boolean = false

/**
 * The resource explorer lets the user browse resources from the provided [AndroidFacet]
 */
class ResourceExplorer private constructor(
  parentDisposable: Disposable,
  facet: AndroidFacet
) : JPanel(BorderLayout()), Disposable {

  var facet by Delegates.observable(facet) { _, _, newValue -> updateFacet(newValue) }

  private val synchronizationManager = SynchronizationManager(facet)
  private val projectResourcesBrowserViewModel = ProjectResourcesBrowserViewModel(facet, synchronizationManager)
  private val importersProvider = ImportersProvider()
  private val resourceImportDragTarget = ResourceImportDragTarget(facet, importersProvider)

  private val resourceExplorerView = ResourceExplorerView(projectResourcesBrowserViewModel, resourceImportDragTarget)

  companion object {

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in an Editor tab
     */
    @Deprecated("use createForToolWindow instead",
                replaceWith = ReplaceWith("ResourceExplorer.createForToolWindow(parentDisposable: Disposable, facet: AndroidFacet)"))
    fun createForEditor(parentDisposable: Disposable, facet: AndroidFacet): ResourceExplorer {
      return ResourceExplorer(
        parentDisposable,
        facet
      )
    }

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in a [com.intellij.openapi.wm.ToolWindow]
     */
    @JvmStatic
    fun createForToolWindow(parentDisposable: Disposable, facet: AndroidFacet): ResourceExplorer {
      return ResourceExplorer(
        parentDisposable,
        facet
      )
    }
  }

  init {
    val configurationManager = ServiceManager.getService(facet.module.project, ImportConfigurationManager::class.java)
    val centerContainer = Box.createVerticalBox()

    @Suppress("ConstantConditionIf")
    if (withExternalBrowser) {
      val fileHelper = ResourceFileHelper.ResourceFileHelperImpl()
      val externalResourceBrowserViewModel = ExternalBrowserViewModel(facet, fileHelper, importersProvider, synchronizationManager)
      val qualifierPanelPresenter = QualifierMatcherPresenter(externalResourceBrowserViewModel::consumeMatcher, configurationManager)
      val qualifierParserPanel = QualifierMatcherPanel(qualifierPanelPresenter)
      val externalResourceBrowser = ExternalResourceBrowser(facet, externalResourceBrowserViewModel, qualifierParserPanel)
      add(externalResourceBrowser, BorderLayout.EAST)
    }
    centerContainer.add(resourceExplorerView)

    @Suppress("ConstantConditionIf")
    if (withDetailView) {
      val designAssetDetailView = DesignAssetDetailView(DesignAssetDetailViewModel(facet.module))
      resourceExplorerView.addSelectionListener(designAssetDetailView)
      centerContainer.add(designAssetDetailView)
    }
    add(centerContainer, BorderLayout.CENTER)
    add(JSlider(JSlider.HORIZONTAL, 100, 300, 300).apply {
      addChangeListener { event ->
        resourceExplorerView.cellWidth = (event.source as JSlider).value
      }
    }, BorderLayout.NORTH)

    Disposer.register(parentDisposable, this)
    Disposer.register(this, synchronizationManager)
  }

  private fun updateFacet(facet: AndroidFacet) {
    projectResourcesBrowserViewModel.facet = facet
    resourceImportDragTarget.facet = facet
  }

  override fun dispose() {
    Disposer.dispose(resourceExplorerView)
  }
}