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
package com.android.tools.idea.npw.template;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Sometimes, there are several separate classes which want to render templates, in some order, but the whole process should be aborted if
 * any of them fail a validation pass. This class acts as a central way to coordinate such render request.
 */
public final class MultiTemplateRender {

  public interface TemplateRenderer {
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

  private final List<TemplateRenderer> myTemplateRenders = new ArrayList<>();
  private boolean myIsCancelled;

  /**
   * Enqueue a template render request, batching it into a collection that will all be validated and, if all valid, rendered, at some
   * later time.
   * Note: This class works by invoking the logic using invokeLater, when we get our first renderer. Any additional renders that are
   * enqueued between now and that later time will be batched. Therefore, if you have any logic that, itself, calls invokeLater between
   * renderer requests, it may break this classes' logic.
   * Note #2: This class is intended to be used once and discarded. If you enqueue renderers after the previous renderers have executed, t
   * his method's behavior may not work as expected.
   */
  public void requestRender(@NotNull TemplateRenderer templateRenderer) {
    if (myIsCancelled) {
      return;
    }

    try {
      myIsCancelled = !templateRenderer.doDryRun();
    }
    catch (Throwable e) {
      myIsCancelled = true;
      throw e;
    }

    if (myIsCancelled) {
      return;
    }

    myTemplateRenders.add(templateRenderer);

    if (myTemplateRenders.size() == 1) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myIsCancelled) {
          return;
        }

        for (TemplateRenderer tr : myTemplateRenders) {
          tr.render();
        }
      });
    }
  }
}
