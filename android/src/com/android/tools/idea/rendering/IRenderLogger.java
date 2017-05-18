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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ILayoutLog;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for logger that records problems it encounters and offers them as
 * a single summary at the end.
 */
public interface IRenderLogger extends ILayoutLog {
  /** Dummy logger producing no side effects. */
  public static final IRenderLogger NULL_LOGGER = new IRenderLogger() {
    private final HtmlLinkManager myLinkManager = new HtmlLinkManager();

    @Override
    public void addMessage(@NotNull RenderProblem message) {
    }

    @Override
    public void addIncorrectFormatClass(@NotNull String className, @NotNull Throwable exception) {
    }

    @Override
    public void addBrokenClass(@NotNull String className, @NotNull Throwable exception) {
    }

    @Override
    public void addMissingClass(@NotNull String className) {
    }

    @Override
    public void setHasLoadedClasses() {
    }

    @Override
    public void setResourceClass(@NotNull String resourceClass) {
    }

    @Override
    public void setMissingResourceClass() {
    }

    @Override
    @NotNull
    public HtmlLinkManager getLinkManager() {
      return myLinkManager;
    }
  };

  public void addMessage(@NotNull RenderProblem message);

  public void addIncorrectFormatClass(@NotNull String className, @NotNull Throwable exception);

  public void addBrokenClass(@NotNull String className, @NotNull Throwable exception);

  public void addMissingClass(@NotNull String className);

  public void setHasLoadedClasses();

  public void setResourceClass(@NotNull String resourceClass);

  public void setMissingResourceClass();

  @NotNull
  public HtmlLinkManager getLinkManager();
}
