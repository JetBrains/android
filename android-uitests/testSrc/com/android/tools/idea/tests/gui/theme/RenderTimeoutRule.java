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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.rendering.RenderService;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.TimeUnit;

/**
 * A {@link TestRule} to set a specific timeout value for the layoutlib render thread.
 */
class RenderTimeoutRule implements TestRule {
  private final long myTimeoutMs;

  public RenderTimeoutRule(long timeout, @NotNull TimeUnit unit) {
    myTimeoutMs = unit.toMillis(timeout);
  }

  @NotNull
  @Override
  public Statement apply(@NotNull Statement base, @NotNull Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        long previousTimeoutMs = RenderService.ourRenderThreadTimeoutMs;
        RenderService.ourRenderThreadTimeoutMs = myTimeoutMs;
        try {
          base.evaluate();
        } finally {
          RenderService.ourRenderThreadTimeoutMs = previousTimeoutMs;
        }
      }
    };
  }
}
