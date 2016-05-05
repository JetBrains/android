/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener interface on various editors.
 */
public interface NlEditingListener {
  /**
   * Default {@link NlEditingListener} which can be used in inspectors if no custom handling is required.
   */
  NlEditingListener DEFAULT_LISTENER = new NlEditingListener() {
    @Override
    public void stopEditing(@NotNull NlComponentEditor source, @Nullable Object value) {
      if (source.getProperty() != null) {
        source.getProperty().setValue(value);
        source.refresh();
      }
    }

    @Override
    public void cancelEditing(@NotNull NlComponentEditor editor) {
    }
  };

  /**
   * The user committed a change in this editor and we can stop editing if this is a table cell.
   * @param editor the editor where the change was committed
   * @param value the new value
   */
  void stopEditing(@NotNull NlComponentEditor editor, @Nullable Object value);

  /**
   * The user cancelled editing in this editor. Revert to the original value and cancel editing if this is a table cell.
   * @param editor the editor where the change was cancelled
   */
  void cancelEditing(@NotNull NlComponentEditor editor);
}


