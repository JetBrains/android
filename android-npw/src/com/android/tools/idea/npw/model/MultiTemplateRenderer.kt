/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.messages.Topic

typealias ProjectRenderRunner = (renderRunnable: (project: Project) -> Unit) -> Unit
/**
 * Sometimes there are several separate classes which want to render templates, in some order, but the whole process should be aborted if
 * any of them fail a validation pass. This class acts as a central way to coordinate such render request.
 *
 * @param renderRunner a lambda which takes a single template renderer as an argument and:
 * 1. Calls it with the right environment (e.g. in the proper thread).
 * 2. Runs optional post-render tasks (e.g. Gradle Sync).
 */
class MultiTemplateRenderer(private val renderRunner: ProjectRenderRunner) {
  interface TemplateRendererListener {
    /**
     * Called just before rendering multiple templates. Since rendering typically involves adding quite a lot of files
     * to the project, this callback is useful to prevent other file-intensive operations such as indexing.
     */
    fun multiRenderingStarted() {}

    /**
     * Called when the last template in the series has been rendered.
     */
    fun multiRenderingFinished() {}
  }

  interface TemplateRenderer {
    /**
     * Runs any needed Model pre-initialisation, for example, setting Template default values.
     */
    @WorkerThread
    fun init() {
    }

    /**
     * Run validation, but don't write any file
     *
     * @return true if the validation succeeded. Returning false will stop any call to [render]
     */
    @WorkerThread
    @Slow
    fun doDryRun(): Boolean

    /**
     * Do the actual work of writing the files.
     */
    @WorkerThread
    @Slow
    fun render()

    /**
     * Runs any needed Model finalization, for example, after generating a project, import it or open generated files on the editor.
     */
    @UiThread
    fun finish() {
    }
  }

  private val templateRenderers: MutableList<TemplateRenderer> = mutableListOf()
  private var requestCount = 1

  /**
   * Call this method to indicate that one more render is available. Every call to this method needs to be later matched by a
   * call to either [requestRender] or [skipRender]
   */
  fun incrementRenders() {
    requestCount++
  }

  /**
   * Enqueue a template render request, batching it into a collection that will all be validated and, if all valid, rendered at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  fun requestRender(templateRenderer: TemplateRenderer) {
    templateRenderers.add(templateRenderer)
    countDown()
  }

  /**
   * Skip a template render request, any pending batching collection will be all validated and, if all valid, rendered at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  fun skipRender() = countDown()

  /**
   * Process batched requests. When all requests are accounted ([incrementRenders] == [requestRender] + [skipRender]), we check that all
   * requests are valid, and if they are, run render them all.
   */
  private fun countDown() {
    check(requestCount != 0) { "Invalid extra call to MultiTemplateRenderer#countDown" }
    requestCount--
    if (requestCount != 0 || templateRenderers.isEmpty()) {
      return
    }
    renderRunner { project ->
      log.info("Generating sources.")
      ApplicationManager.getApplication().assertIsNonDispatchThread();

      multiRenderingStarted(project)

      // Some models need to access other models data, during doDryRun/render phase. By calling init() in all of them first,
      // we make sure they are properly initialized when doDryRun/render is called below.
      with(templateRenderers) {

        forEach(TemplateRenderer::init)
        if (all(TemplateRenderer::doDryRun)) {
          // Run all rendering inside a write lock, so multiple modified files (eg manifest) don't get re-indexed
         TransactionGuard.getInstance().submitTransactionAndWait {
            forEach(TemplateRenderer::render)
          }
        }
      }
      log.info("Generate sources completed.")

      TransactionGuard.getInstance().submitTransactionAndWait {
        // This code needs to run in EDT.
        log.info("Finishing generating sources.")
        templateRenderers.forEach(TemplateRenderer::finish)
        multiRenderingFinished(project)
      }
    }
  }

  companion object {
    private val TEMPLATE_RENDERER_TOPIC =
      Topic("Template rendering", TemplateRendererListener::class.java)

    fun subscribe(project: Project, listener: TemplateRendererListener): MessageBusConnection =
      project.messageBus.connect().apply {
        subscribe(TEMPLATE_RENDERER_TOPIC, listener)
      }

    fun multiRenderingStarted(project: Project) = project.messageBus.syncPublisher(TEMPLATE_RENDERER_TOPIC).multiRenderingStarted()

    fun multiRenderingFinished(project: Project) = project.messageBus.syncPublisher(TEMPLATE_RENDERER_TOPIC).multiRenderingFinished()
  }
}
private val log = logger<MultiTemplateRenderer>()

