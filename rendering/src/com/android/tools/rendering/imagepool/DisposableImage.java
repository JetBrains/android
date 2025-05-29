/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.rendering.imagepool;

/**
 * Interface for images that allow manual disposing. This does not need to be implemented by all
 * {@link ImagePool.Image} implementations.
 */
public interface DisposableImage {
  /**
   * Manually disposes the current image. After calling this method, the image can not be used anymore.
   * <p>
   * This method does not need to be called directly as the images will be eventually collected anyway. However, using this method, you can
   * speed up the collection process to avoid generating extra images.
   */
  void dispose();
}
