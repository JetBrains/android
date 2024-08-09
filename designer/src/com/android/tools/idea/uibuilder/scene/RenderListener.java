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
package com.android.tools.idea.uibuilder.scene;

import com.android.tools.idea.common.scene.SceneManager;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for listeners to be notified about render requests
 */
public interface RenderListener {
  /**
   * Called when the {@link SceneManager} starts a new render request.
   */
  default void onRenderStarted() {}

  /**
   * Called when the {@link SceneManager} has finished a render request
   */
  void onRenderCompleted();

  /**
   * Called when the {@link SceneManager} has failed a render request.
   */
  default void onRenderFailed(@NotNull Throwable e) {}
}
