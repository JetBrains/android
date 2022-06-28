/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.fast

import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditLanguageVersionSettings
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.analyzeSingleDepthInlinedFunctions
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.io.createFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ExecutionException

private val defaultRetryTimes = Integer.getInteger("fast.preview.224875189.retries", 3)

private fun Throwable?.isCompilationError(): Boolean =
  this is LiveEditUpdateException
  && when (error) {
    LiveEditUpdateException.Error.ANALYSIS_ERROR -> message?.startsWith("Analyze Error.") ?: false
    LiveEditUpdateException.Error.COMPILATION_ERROR -> true
    LiveEditUpdateException.Error.UNABLE_TO_INLINE,
    LiveEditUpdateException.Error.NON_PRIVATE_INLINE_FUNCTION,
    LiveEditUpdateException.Error.INTERNAL_ERROR,
    LiveEditUpdateException.Error.UNABLE_TO_LOCATE_COMPOSE_GROUP,
    LiveEditUpdateException.Error.KNOWN_ISSUE -> false
  }

class NonRetriableException(cause: Throwable): Exception(cause)

/**
 * Retries the [retryBlock] [retryTimes] or until it does not throw an exception. The block will be executed in a
 * read action with write priority.
 *
 * This method will also check [ProgressManager.checkCanceled] in between retries.
 *
 * Every retry will wait 50ms * number of retries with a maximum of 200ms per retry.
 */
@VisibleForTesting
fun <T> retryInNonBlockingReadAction(retryTimes: Int = defaultRetryTimes,
                                     indicator: ProgressIndicator = EmptyProgressIndicator(),
                                     retryBlock: () -> T): T {
  var lastException: Throwable? = null
  val result: CompletableDeferred<T> = CompletableDeferred()
  repeat(retryTimes) {
    indicator.checkCanceled()
    try {
      ReadAction.nonBlocking {
        result.complete(retryBlock())
      }
        .wrapProgress(indicator)
        .submit(AndroidExecutors.getInstance().workerThreadExecutor).get()
    }
    catch (t: ProcessCanceledException) {
      // ProcessCanceledException can not be logged
      lastException = t
    }
    catch (t: Throwable) {
      ((t as? ExecutionException)?.cause as? NonRetriableException)?.cause?.let { nonRetriableException ->
        throw nonRetriableException
      }

      Logger.getInstance(EmbeddedCompilerClientImpl::class.java).debug("Retrying after error (retry $it)", t)
      lastException = t
    }
    if (result.isCompleted) return result.getCompleted()
    indicator.checkCanceled()
    Thread.sleep((50 * it).coerceAtMost(200).toLong())
  }
  lastException?.let {
    Logger.getInstance(EmbeddedCompilerClientImpl::class.java).warn("Compile request failed with exception", it)
    throw it
  } ?: throw ProcessCanceledException()
}

/**
 * Implementation of the [CompilerDaemonClient] that uses the embedded compiler in Android Studio. This allows
 * to compile fragments of code in-process similar to how Live Edit does for the emulator.
 *
 * [useInlineAnalysis] should return the value of the Live Edit inline analysis setting.
 */
class EmbeddedCompilerClientImpl(
  private val project: Project,
  private val log: Logger,
  private val useInlineAnalysis: () -> Boolean = { LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis }) : CompilerDaemonClient {

  @TestOnly
  constructor(project: Project, log: Logger, useInlineAnalysis: Boolean): this(project, log, { useInlineAnalysis })

  private val daemonLock = Mutex()
  override val isRunning: Boolean
    get() = daemonLock.holdsLock(this)

  /**
   * The Live Edit inline candidates cache. The cache can only be accessed with the Compile lock (see [runWithCompileLock]).
   * The cache is automatically invalidated on build.
   */
  private val inlineCandidateCache = LiveEditService.getInstance(project).inlineCandidateCache

  /**
   * Compiles the given list of inputs using [module] as context. The output will be generated in the given [outputDirectory] and progress
   * will be updated in the given [ProgressIndicator].
   */
  private fun compileKtFiles(inputs: List<KtFile>, module: Module, outputDirectory: Path, indicator: ProgressIndicator) {
    log.debug("compileKtFile($inputs, $outputDirectory)")

    // Retry is a temporary workaround for b/224875189
    val generationState = retryInNonBlockingReadAction(indicator = indicator) {
      runWithCompileLock {
        log.debug("fetchResolution")
        val resolution = fetchResolution(project, inputs)
        ProgressManager.checkCanceled()
        val languageVersionSettings = inputs.first().languageVersionSettings
        log.debug("analyze")
        val bindingContext = analyze(inputs, resolution)
        val inlineCandidates = inputs
          .flatMap { analyzeSingleDepthInlinedFunctions(resolution, it, bindingContext, inlineCandidateCache) }
          .toSet()
        ProgressManager.checkCanceled()
        log.debug("backCodeGen")
        try {
          backendCodeGen(project, resolution, bindingContext, inputs, module, inlineCandidates,
                         AndroidLiveEditLanguageVersionSettings(languageVersionSettings))
        }
        catch (e: LiveEditUpdateException) {
          if (e.isCompilationError() || e.cause.isCompilationError()) {
            log.debug("backCodeGen compilation exception ", e)
            throw NonRetriableException(e)
          }

          if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE || !useInlineAnalysis()) {
            log.debug("backCodeGen exception ", e)
            throw e
          }

          // Add any extra source file this compilation need in order to support the input file calling an inline function
          // from another source file then perform a compilation again.
          log.debug("inline analysis")
          val inputFilesWithInlines = inputs.flatMap {
            performInlineSourceDependencyAnalysis(resolution, it, bindingContext)
          }

          // We need to perform the analysis once more with the new set of input files.
          log.debug("inline analysis with inlines ${inputFilesWithInlines.joinToString(",") { it.name }}")
          val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFilesWithInlines)

          // We will need to start using the binding context from the new analysis for code gen.
          val newBindingContext = newAnalysisResult.bindingContext

          log.debug("backCodeGen retry")
          backendCodeGen(project, resolution, newBindingContext, inputFilesWithInlines, module, inlineCandidates,
                         AndroidLiveEditLanguageVersionSettings(languageVersionSettings))
        }
      }
    }
    log.debug("backCodeGen completed")

    generationState.factory.asList().forEach {
      log.debug("output: ${it.relativePath}")
      val path = outputDirectory.resolve(it.relativePath)
      path.createFile()
      Files.write(path, it.asByteArray())
    }
  }

  override suspend fun compileRequest(
    files: Collection<PsiFile>,
    module: Module,
    outputDirectory: Path,
    indicator: ProgressIndicator): CompilationResult = coroutineScope {
    daemonLock.lock(this)
    return@coroutineScope try {
      val inputs = files.filterIsInstance<KtFile>().toList()
      val result = CompletableDeferred<CompilationResult>()
      val compilationIndicator = ProgressWrapper.wrap(indicator)

      // When the coroutine completes, make sure we also stop or cancel the indicator
      // depending on what happened with the co-routine.
      coroutineContext.job.invokeOnCompletion {
        try {
          if (compilationIndicator.isRunning) {
            if (coroutineContext.job.isCancelled) compilationIndicator.cancel() else compilationIndicator.stop()
          }
        }
        catch (t: Throwable) {
          // stop might throw if the indicator is already stopped
          log.warn(t)
        }
      }

      try {
        compileKtFiles(inputs, module, outputDirectory = outputDirectory, compilationIndicator)
        result.complete(CompilationResult.Success)
      }
      catch (t: CancellationException) {
        result.complete(CompilationResult.CompilationAborted())
      }
      catch (t: ProcessCanceledException) {
        result.complete(CompilationResult.CompilationAborted())
      }
      catch (t: Throwable) {
        if (t.isCompilationError() || t.cause.isCompilationError())
          result.complete(CompilationResult.CompilationError(t))
        else
          result.complete(CompilationResult.RequestException(t))
      }

      result.await()
    }
    finally {
      daemonLock.unlock(this)
    }
  }

  override fun dispose() {}
}