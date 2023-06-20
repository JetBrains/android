/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.common.model

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.configurations.Configuration
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly
import java.util.function.BiFunction
import java.util.function.Consumer

/**
 * Interface to be implemented by factories that can produce {@link NlModel}s from {@link NlModelBuilder}.
 * The main use is to allow to re-use the builder for testing and produce <code>SyncNlModel</code> instead of {@link NlModel}.
 */
interface NlModelFactoryInterface {
  fun build(nlModelBuilder: NlModelBuilder): NlModel
}

/**
 * An {@link NlModel} builder
 */
// TODO: Migrate next to NlModel if/when NlModel is migrated to Kotlin
class NlModelBuilder constructor(val facet: AndroidFacet, val file: VirtualFile, val configuration: Configuration) {
  private var modelFactory: NlModelFactoryInterface = object : NlModelFactoryInterface {
    override fun build(nlModelBuilder: NlModelBuilder): NlModel = with(nlModelBuilder) {
      NlModel.create(
        parentDisposable,
        modelTooltip,
        facet,
        file,
        configuration,
        componentRegistrar,
        xmlFileProvider,
        modelUpdater,
        dataContext)
    }
  }

  private var parentDisposable: Disposable? = null
  private var modelTooltip: String? = null
  private var componentRegistrar: Consumer<NlComponent> = Consumer {}
  private var xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile> = BiFunction { project, virtualFile ->
    getDefaultFile(project, virtualFile)
  }
  private var modelUpdater: NlModel.NlModelUpdaterInterface? = null
  private var dataContext: DataContext = DataContext.EMPTY_CONTEXT

  /**
   * Method to be used to customize the instantiation of [NlModel]. Used for testing to allow
   * creating subclasses of NlModel.
   */
  @TestOnly
  fun useNlModelFactory(modelFactory: NlModelFactoryInterface): NlModelBuilder = also {
    this.modelFactory = modelFactory
  }

  fun withParentDisposable(parentDisposable: Disposable): NlModelBuilder = also {
    this.parentDisposable = parentDisposable
  }

  fun withModelTooltip(modelTooltip: String): NlModelBuilder = also {
    this.modelTooltip = modelTooltip
  }

  fun withComponentRegistrar(componentRegistrar: Consumer<NlComponent>): NlModelBuilder = also {
    this.componentRegistrar = componentRegistrar
  }

  fun withXmlProvider(xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>): NlModelBuilder = also {
    this.xmlFileProvider = xmlFileProvider
  }

  fun withModelUpdater(modelUpdater: NlModel.NlModelUpdaterInterface): NlModelBuilder = also {
    this.modelUpdater = modelUpdater
  }

  fun withDataContext(dataContext: DataContext): NlModelBuilder = also {
    this.dataContext = dataContext
  }

  @Slow
  fun build(): NlModel = modelFactory.build(this)

  companion object {
    public fun getDefaultFile(project: Project, virtualFile: VirtualFile) =
      AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as XmlFile
  }
}