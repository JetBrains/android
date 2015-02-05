/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.value;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Facades functionality of exposing available values for particular {@link GradleEditorEntity}.
 * <p/>
 * E.g. there might be an implementation which knows how to retrieve list of available versions for particular external library.
 */
public interface GradleEditorEntityValueManager {

  GradleEditorEntityValueManager NO_OP = new GradleEditorEntityValueManager() {
    @Nullable
    @Override
    public String validate(@NotNull String newValue, boolean strict) {
      return null;
    }

    @Override
    public boolean isAvailableVersionsHintReady() {
      return true;
    }

    @Nullable
    @Override
    public List<String> hintAvailableVersions() {
      return Collections.emptyList();
    }
  };

  /**
   * Asks to check if given value might be used as a value for the target property managed by the current value manager.
   *
   * @param newValue  candidate value
   * @param strict    a flag which hints current manager if it should ensure that given value belongs to the
   *                  {@link #hintAvailableVersions() available values collection}
   * @return          <code>null</code> as an indication that given value is validated and can be used;
   *                  an error description otherwise
   */
  @Nullable
  String validate(@NotNull String newValue, boolean strict);

  /**
   * @return    <code>true</code> if {@link #hintAvailableVersions() previously request for available versions} is complete;
   *            <code>false</code> otherwise
   */
  boolean isAvailableVersionsHintReady();

  /**
   * Asks current manager to build list of available versions.
   * <p/>
   * This might require I/O (e.g. fetch information about target library version from remote repo), so, such processing is
   * just triggered by the current method call and is actually performed in background.
   * <p/>
   * {@link #isAvailableVersionsHintReady()} is assumed to return <code>'false'</code> as long as the processing is not finished.
   *
   * @return    <code>null</code> if list of available versions is not available yet;
   *            list of available versions (e.g. built during previous call to this method) otherwise
   */
  @Nullable
  List<String> hintAvailableVersions();
}
