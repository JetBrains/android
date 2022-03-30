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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditLanguageVersionSettings
import com.android.tools.idea.run.deployment.liveedit.analyze
import com.android.tools.idea.run.deployment.liveedit.backendCodeGen
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.io.createFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Files
import java.nio.file.Path

private val defaultRetryTimes = Integer.getInteger("fast.preview.224875189.retries", 3)

/**
 * Retries the [retryBlock] [retryTimes] or until it does not throw an exception.
 *
 * Every retry will wait 50ms * number of retries with a maximum of 200ms per retry.
 */
@VisibleForTesting
fun <T> retry(retryTimes: Int = defaultRetryTimes, retryBlock: () -> T): T {
  var lastException: Throwable? = null
  repeat(retryTimes) {
    ProgressManager.checkCanceled()
    try {
      return retryBlock()
    }
    catch (t: ProcessCanceledException) {
      throw t // Immediately exit, no more retries
    }
    catch (t: Throwable) {
      Logger.getInstance(EmbeddedCompilerClientImpl::class.java).warn("Retrying after error (retry $it)", t)
      lastException = t
    }
    ProgressManager.checkCanceled()
    Thread.sleep((50 * it).coerceAtMost(200).toLong())
  }
  lastException?.let { throw it } ?: throw IllegalStateException()
}

/**
 * Implementation of the [CompilerDaemonClient] that uses the embedded compiler in Android Studio. This allows
 * to compile fragments of code in-process similar to how Live Edit does for the emulator.
 */
class EmbeddedCompilerClientImpl(private val project: Project, private val log: Logger) : CompilerDaemonClient {
  private val daemonLock = Mutex()
  override val isRunning: Boolean
    get() = daemonLock.holdsLock(this)

  private fun compileKtFiles(inputs: List<KtFile>, outputDirectory: Path) {
    log.debug("compileKtFile($inputs, $outputDirectory)")

    ProgressManager.checkCanceled()
    log.debug("fetchResolution")
    val resolution = KotlinCacheService.getInstance(project).getResolutionFacade(inputs)
    val languageVersionSettings = inputs.first().languageVersionSettings

    ProgressManager.checkCanceled()

    // Retry is a temporary workaround for b/224875189
    val generationState = retry {
      log.debug("analyze")
      val bindingContext = analyze(inputs, resolution)
      ProgressManager.checkCanceled()
      log.debug("backCodeGen")
      backendCodeGen(project, resolution, bindingContext, inputs,
                     AndroidLiveEditLanguageVersionSettings(languageVersionSettings))
    }

    generationState.factory.asList().forEach {
      val path = outputDirectory.resolve(it.relativePath)
      path.createFile()
      Files.write(path, it.asByteArray())
    }
  }

  override suspend fun compileRequest(
    files: Collection<PsiFile>,
    module: Module,
    outputDirectory: Path,
    indicator: ProgressIndicator): CompilationResult {
    daemonLock.lock(this)
    return try {
      val inputs = files.filterIsInstance<KtFile>().toList()
      val result = CompletableDeferred<CompilationResult>()
      val initialRetries = 3
      var retries = initialRetries
      var readActionExecuted: Boolean

      // This code will retry the execute action 3 times. The compile action can be cancelled if any write action happens during the
      // compilation.
      do {
        if (retries != initialRetries) delay((initialRetries - retries) * 50L)
        readActionExecuted = ProgressManager.getInstance().runInReadActionWithWriteActionPriority(
          {
            try {
              compileKtFiles(inputs, outputDirectory = outputDirectory)
              result.complete(CompilationResult.Success)
            }
            catch (t: Throwable) {
              result.complete(CompilationResult.RequestException(t))
            }
          },
          ProgressWrapper.wrap(indicator)
        )
      } while (!readActionExecuted && retries-- > 0)

      if (readActionExecuted)
        result.await()
      else CompilationResult.CompilationAborted(Throwable("Unable to start read action"))
    }
    finally {
      daemonLock.unlock(this)
    }
  }

  override fun dispose() {}
}