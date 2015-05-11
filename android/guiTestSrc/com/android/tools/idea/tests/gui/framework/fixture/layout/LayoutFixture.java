/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture wrapping the the layout preview window or the layout editor.
 * This interface allows various shared rendering test infrastructure
 * (such as the {@link ConfigurationToolbarFixture}) which is present
 * in both the layout preview tool window and in the layout editor to
 * interact with both types of rendering containers.
 */
public interface LayoutFixture {
  @NotNull
  ConfigurationToolbarFixture getToolbar();

  /**
   * Returns the associated errors fixture.
   */
  @NotNull
  RenderErrorPanelFixture getRenderErrors();

  /**
   * Waits for render to finish, if render is in progress. Otherwise returns immediately.
   * Returns a token which can be used to check whether the rendering has changed since a previous render wait
   */
  @NotNull
  Object waitForRenderToFinish();

  /**
   * Waits for the next render to finish. This method guarantees that it will wait for a more recent
   * render than the last call to this method. This is a convenience implementation on top of
   * {@link #waitForNextRenderToFinish(Object)} where the fixture keeps track of the most recent
   * rendering token on its own and passes it to the next call. Whereas {@link #waitForRenderToFinish()}
   * can be a no-op if there is already a rendering result present, this method is only a no-op
   * the first time it is called (if there is already a rendering result available).
   */
  void waitForNextRenderToFinish();

  /**
   * Waits for render to finish, if render is in progress. Otherwise returns immediately.
   * @param previous If not null, represents a rendering token (previously returned from this method) which
   *    we don't want to return; we expect a more recent rendering to be processed and will wait for it
   */
  @NotNull
  Object waitForNextRenderToFinish(@Nullable Object previous);

  /**
   * Asserts that the render was successful with no errors or warnings. Will wait for render to finish first if it's in progress.
   */
  void requireRenderSuccessful();

  /** Asserts that the render was successful, optionally with some errors or no errors at all */
  void requireRenderSuccessful(boolean allowErrors, boolean allowWarnings);
}
