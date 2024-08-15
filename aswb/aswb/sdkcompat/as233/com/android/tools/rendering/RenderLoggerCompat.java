/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.rendering;

import java.util.Map;
import java.util.Set;

/** Compat class for RenderLogger. */
public class RenderLoggerCompat {
  private final RenderLogger renderLogger;

  public RenderLoggerCompat(RenderResultCompat result) {
    renderLogger = result.getLogger();
  }

  public RenderLogger get() {
    return renderLogger;
  }

  public boolean hasErrors() {
    return renderLogger.hasErrors();
  }

  public Map<String, Throwable> getBrokenClasses() {
    return renderLogger.getBrokenClasses();
  }

  public Set<String> getMissingClasses() {
    return renderLogger.getMissingClasses();
  }

  public static void resetFidelityErrorsFilters() {
    RenderLogger.resetFidelityErrorsFilters();
  }
}
