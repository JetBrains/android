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
package com.android.tools.idea.rendering

/**
 * Extension implementing some heuristics to detect custom View rendering errors. This allows to
 * identify render errors better.
 * @param customViewFqcn
 */
fun RenderResult?.isErrorResult(customViewFqcn: String): Boolean {
  if (this == null) {
    return true
  }

  // Renders might fail with onLayout exceptions hiding actual errors. This will return an
  // empty image
  // result. We can detect this by checking for a 1x1 or 0x0 image and the logger having errors.
  if (logger.hasErrors() && renderedImage.width <= 1 && renderedImage.height <= 1) {
    return true
  }

  return logger.brokenClasses.values.any {
    it is ReflectiveOperationException &&
    it.stackTrace.any { ex -> customViewFqcn == ex.className }
  }
}