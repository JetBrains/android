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
package com.android.tools.idea.welcome.install

import com.google.common.base.Function
import com.google.common.base.Throwables
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Operation that is executed when installing the operation.
 *
 * Type argument specifies the type of the operation return value and the argument
 */
abstract class InstallOperation<Return, Argument>(
  protected val context: InstallContext, // TODO: replace with ProgressIndicator.createSubProgress
  private val progressRatio: Double
) {
  /**
   * Performs the actual logic
   */
  @Throws(WizardException::class, InstallationCancelledException::class)
  protected abstract fun perform(indicator: ProgressIndicator, argument: Argument): Return

  /**
   * Runs the operation under progress indicator that only gives access to progress portion.
   */
  @Throws(WizardException::class, InstallationCancelledException::class)
  fun execute(argument: Argument): Return {
    context.checkCanceled()
    return if (progressRatio == 0.0) {
      perform(EmptyProgressIndicator(), argument)
    }
    else {
      try {
        context.run(ThrowableComputable<Return, Exception> {
          val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
          perform(indicator, argument)
        }, progressRatio)
      }
      catch (e: ProcessCanceledException) {
        throw InstallationCancelledException()
      }
      catch (e: Exception) {
        Throwables.propagateIfPossible(e, WizardException::class.java, InstallationCancelledException::class.java)
        throw RuntimeException(e)
      }
    }
  }

  /**
   * Shows a retry prompt. Throws an exception to stop the setup process if the user preses cancel or returns normally otherwise.
   */
  @Throws(WizardException::class)
  protected fun promptToRetry(prompt: String, failureDescription: String, e: Exception?) {
    val response = AtomicBoolean(false)
    val application = ApplicationManager.getApplication()
    application.invokeAndWait(
      {
        val wrappedPrompt = prompt.replace("(\\p{Print}{30,50}([\\h\\n]|$))".toRegex(), "$1\n")
        val wrappedFailure = failureDescription
          .replace("(\\p{Print}{30,50}([\\h\\n]|$))".toRegex(), "$1\n")
          .replace("(\\p{Print}{30,50}/)".toRegex(), "$1\n")
        val i = Messages.showDialog(
          null, wrappedPrompt, "Android Studio Setup", wrappedFailure, arrayOf("Retry", "Cancel"),
          0, 0, Messages.getErrorIcon()
        )
        response.set(i == Messages.YES)
      }, application.anyModalityState)
    if (!response.get()) {
      if (e != null) {
        Throwables.throwIfInstanceOf(e, WizardException::class.java)
      }
      throw WizardException(failureDescription, e)
    }
    else {
      context.print(failureDescription + "\n", ConsoleViewContentType.ERROR_OUTPUT)
    }
  }

  abstract fun cleanup(result: Return)

  /**
   * This allows combining a sequence of operations into one, assisting with the cleanup.
   */
  fun <FinalResult> then(next: InstallOperation<FinalResult, Return>): InstallOperation<FinalResult, Argument> {
    return OperationChain(this, next)
  }

  /**
   * Adds a function to a sequence, wrapping it into InstallOperation.
   *
   * Note that currently it is expected that the function is fast and there is no progress to report.
   * Another option is to manage progress manually.
   */
  fun <FinalResult> then(next: Function<Return, FinalResult>): InstallOperation<FinalResult, Argument> = then(wrap(context, next, 0.0))

  private class OperationChain<FinalResult, Argument, Return>(
    private val first: InstallOperation<Return, Argument>,
    private val second: InstallOperation<FinalResult, Return>
  ) : InstallOperation<FinalResult, Argument>(
    first.context, 0.0) {
    @Throws(WizardException::class, InstallationCancelledException::class)
    override fun perform(indicator: ProgressIndicator, argument: Argument): FinalResult {
      val firstResult = first.execute(argument)
      return try {
        second.execute(firstResult)
      }
      finally {
        first.cleanup(firstResult)
      }
    }

    override fun cleanup(result: FinalResult) {
      second.cleanup(result)
    }
  }

  private class FunctionWrapper<Return, Argument>(
    context: InstallContext,
    private val runnable: Function<Argument, Return>,
    progressShare: Double
  ) : InstallOperation<Return, Argument>(context, progressShare) {
    override fun perform(indicator: ProgressIndicator, argument: Argument): Return {
      indicator.start()
      return try {
        runnable.apply(argument)!!
      }
      finally {
        indicator.fraction = 1.0
      }
    }

    override fun cleanup(result: Return) {
      // Do nothing
    }
  }

  companion object {
    @JvmStatic
    fun <Return, Argument> wrap(
      context: InstallContext, function: Function<Argument, Return>, progressShare: Double
    ): InstallOperation<Return, Argument> = FunctionWrapper(context, function, progressShare)
  }
}