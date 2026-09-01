/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.services

import com.android.annotations.concurrency.GuardedBy
import com.android.repository.api.RepoManager
import com.android.template.engine.DefaultTemplateMessageSink
import com.android.template.engine.TemplateDefinition
import com.android.template.engine.TemplateEngineFactory
import com.android.template.engine.TemplateList
import com.android.template.engine.TemplateMessageSink
import com.android.template.engine.copyAndLoadExtraFiles
import com.android.tools.idea.npw.template.WizardPluginPromotionTemplateProvider
import com.android.tools.idea.npw.template.toPromotionCardSpec
import com.android.tools.idea.npw.templateengine.WizardConstants
import com.android.tools.idea.npw.templateengine.api.ExternalTemplateSpec
import com.android.tools.idea.npw.templateengine.api.PromotionCardSpec
import com.android.tools.idea.npw.templateengine.api.TemplateEngineProjectWizardContributor
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.sdk.AndroidSdkData
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.APP)
class TemplateRegistryService
@JvmOverloads
constructor(private val coroutineScope: CoroutineScope, private val zipPathProvider: (Path?) -> Path = ::getDefaultZipPath) : Disposable {

  private val logger = logger<TemplateRegistryService>()
  private val lock = Any()

  @GuardedBy("lock") private var templateList: TemplateList = TemplateList(emptyList())

  @GuardedBy("lock") private var lastErrorMessage: String? = null

  @GuardedBy("lock") private var currentRepoManager: RepoManager? = null

  @GuardedBy("lock") private var currentSdkPath: Path? = null

  private val localChangeListener = RepoManager.RepoLoadedListener {
    logger.debug("SDK packages changed, reloading templates")
    coroutineScope.launch(Dispatchers.IO) { loadTemplatesAndResources() }
  }

  private val sdkPathFlow = MutableSharedFlow<Path>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      val initialSdkPath = IdeSdks.getInstance().androidSdkPath?.toPath()
      if (initialSdkPath != null) {
        synchronized(lock) { currentSdkPath = initialSdkPath }
        sdkPathFlow.emit(initialSdkPath)
      }
      sdkPathFlow.distinctUntilChanged().collect { path ->
        synchronized(lock) {
          currentSdkPath = path
          currentRepoManager?.removeLocalChangeListener(localChangeListener)
          currentRepoManager = null

          val sdkData = AndroidSdkData.getSdkData(path)
          if (sdkData != null) {
            val progress = StudioLoggerProgressIndicator(TemplateRegistryService::class.java)
            val repoManager = sdkData.sdkHandler.getRepoManager(progress)
            repoManager.addLocalChangeListener(localChangeListener)
            currentRepoManager = repoManager
          } else {
            logger.warn("Could not get SdkData for $path, TemplateRegistry won't receive package updates")
          }
        }
        withContext(Dispatchers.IO) { loadTemplatesAndResources() }
      }
    }
  }

  fun onSdkPathChanged(newSdkPath: Path) {
    sdkPathFlow.tryEmit(newSdkPath)
  }

  fun getTemplateList(): TemplateList = synchronized(lock) { templateList }

  fun getTemplateDefinitions(): List<TemplateDefinition> = synchronized(lock) { templateList.templates }

  fun getPromotionCards(): List<PromotionCardSpec> =
    WizardPluginPromotionTemplateProvider.getAllPromotionTemplates()
      .filterNot { PluginManagerCore.isPluginInstalled(PluginId.getId(it.pluginId)) }
      .map { it.toPromotionCardSpec() }

  fun getContributorExternalTemplates(): List<ExternalTemplateSpec> =
    TemplateEngineProjectWizardContributor.EP_NAME.extensionList.sortedByDescending { it.priority }.flatMap { it.getExternalTemplates() }

  fun getLastErrorMessage(): String? = synchronized(lock) { lastErrorMessage }

  fun loadTemplatesAndResources() {
    try {
      val sdkPath = synchronized(lock) { currentSdkPath }
      val zipPath = zipPathProvider(sdkPath)
      val loadedList = loadTemplatesFromZip(zipPath)
      synchronized(lock) {
        templateList = loadedList
        lastErrorMessage = null
      }
    } catch (e: ZipException) {
      val error = "Corrupt template package: ${e.message}. Run 'android sdk install build/templates' in SDK Manager."
      logger.warn(error)
      synchronized(lock) {
        lastErrorMessage = error
        templateList = TemplateList(emptyList())
      }
    } catch (e: Exception) {
      val error = "Templates unavailable: ${e.message ?: e.javaClass.simpleName}"
      logger.warn(error)
      synchronized(lock) {
        lastErrorMessage = error
        templateList = TemplateList(emptyList())
      }
    }
  }

  private fun loadTemplatesFromZip(zipPath: Path): TemplateList {
    val factory = TemplateEngineFactory.createDefault { template ->
      // Currently loading only Gradle templates
      template.metadata.tags.any { it.startsWith("agp") || it.startsWith("gradle") }
    }
    val builder = factory.createTemplateListBuilder(messageSink)
    builder.loadFromZipFile(zipPath)
    return builder.toTemplateList().copyAndLoadExtraFiles()
  }

  override fun dispose() {
    synchronized(lock) {
      currentRepoManager?.removeLocalChangeListener(localChangeListener)
      currentRepoManager = null
    }
  }

  companion object {
    val messageSink =
      object : DefaultTemplateMessageSink(TemplateMessageSink.Severity.Verbose) {
        private val sinkLogger = Logger.getInstance("TemplateEngine")

        override fun onMessage(entry: MessageEntry) {
          val msg = entry.message
          when (entry.severity) {
            TemplateMessageSink.Severity.Error -> sinkLogger.error(msg)
            TemplateMessageSink.Severity.Warn -> sinkLogger.warn(msg)
            TemplateMessageSink.Severity.Info -> sinkLogger.info(msg)
            TemplateMessageSink.Severity.Verbose,
            TemplateMessageSink.Severity.Debug -> sinkLogger.debug(msg)
          }
        }
      }

    fun getInstance(): TemplateRegistryService = service()

    @TestOnly
    fun createForTest(
      coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
      zipPathProvider: (Path?) -> Path = ::getDefaultZipPath,
    ): TemplateRegistryService = TemplateRegistryService(coroutineScope, zipPathProvider)
  }
}

class TemplateRegistrySdkEventListener : IdeSdks.AndroidSdkEventListener {
  override fun afterSdkPathChange(sdkPath: File, project: Project) {
    TemplateRegistryService.getInstance().onSdkPathChanged(sdkPath.toPath())
  }
}

private fun getDefaultZipPath(sdkPath: Path?): Path {
  val path = sdkPath ?: throw IllegalStateException("Android SDK path is not configured")
  val zipPath = path.resolve(WizardConstants.RELATIVE_PATH_TEMPLATES_ZIP)
  if (!Files.exists(zipPath)) {
    throw IllegalStateException("Templates package not found at $zipPath")
  }
  return zipPath
}
