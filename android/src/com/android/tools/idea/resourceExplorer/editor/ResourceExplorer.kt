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
import com.android.tools.idea.resourceExplorer.view.DesignAssetDetailView
import com.android.tools.idea.resourceExplorer.view.ExternalResourceBrowser
import com.android.tools.idea.resourceExplorer.view.ModuleResourceBrowser
import com.android.tools.idea.resourceExplorer.view.QualifierMatcherPanel
import com.android.tools.idea.resourceExplorer.viewmodel.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.JSlider

/**
 * The resource explorer lets the user browse resources from the provided [AndroidFacet]
 */
class ResourceExplorer private constructor(
  parentDisposable: Disposable,
  facet: AndroidFacet,
  withExternalBrowser: Boolean = false,
  withDetailView: Boolean = false,
  withSections: Boolean = false
) : JPanel(BorderLayout()) {

  companion object {

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in an Editor tab
     */
    fun createForEditor(parentDisposable: Disposable, facet: AndroidFacet): JPanel {
      return ResourceExplorer(
        parentDisposable,
        facet,
        withDetailView = true,
        withExternalBrowser = true,
        withSections = true
      )
    }

    /**
     * Create a new instance of [ResourceExplorer] optimized to be used in a [com.intellij.openapi.wm.ToolWindow]
     */
    fun createForToolWindow(parentDisposable: Disposable, facet: AndroidFacet): JPanel {
      return ResourceExplorer(
        parentDisposable,
        facet,
        withSections = true
      )
    }
  }

  init {
    val synchronizationManager = SynchronizationManager(facet)
    val fileHelper = ResourceFileHelper.ResourceFileHelperImpl()
    val importersProvider = ImportersProvider()
    val configurationManager = ServiceManager.getService(facet.module.project, ImportConfigurationManager::class.java)
    val centerContainer = Box.createVerticalBox()

    if (withExternalBrowser) {
      val externalResourceBrowserViewModel = ExternalBrowserViewModel(facet, fileHelper, importersProvider, synchronizationManager)
      val qualifierPanelPresenter = QualifierMatcherPresenter(externalResourceBrowserViewModel::consumeMatcher, configurationManager)
      val qualifierParserPanel = QualifierMatcherPanel(qualifierPanelPresenter)
      val externalResourceBrowser = ExternalResourceBrowser(facet, externalResourceBrowserViewModel, qualifierParserPanel)
      add(externalResourceBrowser, BorderLayout.EAST)
    }

    val internalResourceBrowser = ModuleResourceBrowser(ModuleResourcesBrowserViewModel(facet, synchronizationManager))
    centerContainer.add(internalResourceBrowser)

    if (withDetailView) {
      val designAssetDetailView = DesignAssetDetailView(DesignAssetDetailViewModel(facet.module))
      internalResourceBrowser.addSelectionListener(designAssetDetailView)
      centerContainer.add(designAssetDetailView)
    }
    add(centerContainer, BorderLayout.CENTER)
    add(JSlider(JSlider.HORIZONTAL, 100, 300, 300).apply {
      addChangeListener { event ->
        internalResourceBrowser.cellWidth = (event.source as JSlider).value
      }
    }, BorderLayout.NORTH)
    Disposer.register(parentDisposable, synchronizationManager)
  }
}