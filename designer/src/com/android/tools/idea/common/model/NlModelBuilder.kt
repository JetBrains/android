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
import com.android.tools.configurations.Configuration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.rendering.BuildTargetReference
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import java.util.function.BiFunction
import java.util.function.Consumer

/**
 * Interface to be implemented by factories that can produce {@link NlModel}s from {@link
 * NlModelBuilder}. The main use is to allow to re-use the builder for testing and produce
 * <code>SyncNlModel</code> instead of {@link NlModel}.
 */
interface NlModelFactoryInterface {
  fun build(nlModelBuilder: NlModelBuilder): NlModel
}

/** An {@link NlModel} builder */
// TODO: Migrate next to NlModel if/when NlModel is migrated to Kotlin
class NlModelBuilder(
  val parentDisposable: Disposable,
  val buildTarget: BuildTargetReference,
  val file: VirtualFile,
  val configuration: Configuration,
) {
  private var modelFactory: NlModelFactoryInterface =
    object : NlModelFactoryInterface {
      override fun build(nlModelBuilder: NlModelBuilder): NlModel =
        with(nlModelBuilder) {
          NlModel.create(
              parentDisposable,
              buildTarget,
              file,
              configuration,
              componentRegistrar,
              xmlFileProvider,
              modelUpdater,
              dataContext,
            )
            .apply { setTooltip(this@NlModelBuilder.tooltip) }
        }
    }

  private var tooltip: String? = null
  private var componentRegistrar: Consumer<NlComponent> = Consumer {}
  private var xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile> =
    BiFunction { project, virtualFile ->
      getDefaultFile(project, virtualFile)
    }
  private var modelUpdater: NlModelUpdaterInterface? = null
  private var dataContext: DataContext = DataContext.EMPTY_CONTEXT

  fun withModelTooltip(modelTooltip: String): NlModelBuilder = also { this.tooltip = modelTooltip }

  fun withComponentRegistrar(componentRegistrar: Consumer<NlComponent>): NlModelBuilder = also {
    this.componentRegistrar = componentRegistrar
  }

  fun withXmlProvider(xmlFileProvider: BiFunction<Project, VirtualFile, XmlFile>): NlModelBuilder =
    also {
      this.xmlFileProvider = xmlFileProvider
    }

  fun withModelUpdater(modelUpdater: NlModelUpdaterInterface): NlModelBuilder = also {
    this.modelUpdater = modelUpdater
  }

  fun withDataContext(dataContext: DataContext): NlModelBuilder = also {
    this.dataContext = dataContext
  }

  @Slow fun build(): NlModel = modelFactory.build(this)

  companion object {
    fun getDefaultFile(project: Project, virtualFile: VirtualFile) =
      AndroidPsiUtils.getPsiFileSafely(project, virtualFile) as XmlFile
  }
}
