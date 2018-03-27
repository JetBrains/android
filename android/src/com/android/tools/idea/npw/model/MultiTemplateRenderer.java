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
package com.android.tools.idea.npw.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Sometimes, there are several separate classes which want to render templates, in some order, but the whole process should be aborted if
 * any of them fail a validation pass. This class acts as a central way to coordinate such render request.
 */
public final class MultiTemplateRenderer {

  public interface TemplateRenderer {
    /**
     * Runs any needed Model pre-initialisation, for example, setting Template default values.
     */
    default void init() {}

    /**
     * Run validation, but don't write any file
     * @return true if the validation succeeded. Returning false will stop any call to {@link #render()}
     */
    boolean doDryRun();

    /**
     * Do the actual work of writing the files.
     */
    void render();
  }

  private final List<TemplateRenderer> myTemplateRenderers = new ArrayList<>();
  private int myRequestCount = 1;

  /**
   * Call this method to indicate that one more render is available. Every call to this method needs to be later matched by a
   * call to either {@link #requestRender(TemplateRenderer)} or {@link #skipRender()}
   */
  public void incrementRenders() {
    myRequestCount++;
  }

  /**
   * Enqueue a template render request, batching it into a collection that will all be validated and, if all valid, rendered, at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  public void requestRender(@NotNull TemplateRenderer templateRenderer) {
    myTemplateRenderers.add(templateRenderer);
    countDown();
  }

  /**
   * Skip a template render request, any pending batching collection will be all validated and, if all valid, rendered, at some
   * later time.
   * Note: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed,
   * this method's behavior may not work as expected.
   */
  public void skipRender() {
    countDown();
  }

  /**
   * Process batched requests. When all requests are accounted (#incrementRenders == #requestRender + #skipRender), we check that all
   * requests are valid, and if they are, run render them all.
   */
  private void countDown() {
    if (myRequestCount == 0) {
      throw new IllegalStateException("Invalid extra call to MultiTemplateRenderer#countDown");
    }
    myRequestCount--;

    if (myRequestCount == 0) {
      // Some models need to access other models data, during doDryRun/render phase. By calling init() in all of them first, we make sure
      // they are properly initialized when doDryRun/render is called bellow.
      for (TemplateRenderer renderer : myTemplateRenderers) {
        renderer.init();
      }

      for (TemplateRenderer renderer : myTemplateRenderers) {
        if (!renderer.doDryRun()) {
          return;
        }
      }

      for (TemplateRenderer renderer : myTemplateRenderers) {
        renderer.render();
      }
    }
  }
}
