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
package com.android.tools.idea.gradle.editor.ui;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class GradleEditorUiConstants {

  public static final DataKey<GradleEditorEntity> ACTIVE_ENTITY_KEY = DataKey.create("Gradle.Editor.Entity.Active");

  public static final Color BACKGROUND_COLOR = UIUtil.getTableBackground();
  public static final Color INJECTED_BACKGROUND_COLOR;
  public static final Color OUTGOING_BACKGROUND_COLOR;
  static {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    INJECTED_BACKGROUND_COLOR = scheme.getAttributes(EditorColors.INJECTED_LANGUAGE_FRAGMENT).getBackgroundColor();
    OUTGOING_BACKGROUND_COLOR = scheme.getAttributes(EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES).getBackgroundColor();
  }

  public static final int ANIMATION_TIME_MILLIS = 300;
  public static final int DEFAULT_ENTITY_UI_ORDER = 100;

  public static final String GRADLE_EDITOR_TABLE_PLACE = "GRADLE_EDITOR_TABLE";

  /**
   * We want to show a toolbar when a mouse is over gradle entity row (with buttons like 'help', 'go to source' etc).
   * <p/>
   * However, we don't want to do that immediately when mouse enters particular entity's row (as it would look messy when a user
   * quickly moves mouse over multiple rows), so, we use delay defined at the current constant to show the toolbar if the mouse
   * is still over the component's row.
   */
  public static final int ENTITY_TOOLBAR_APPEARANCE_DELAY_MILLIS = 300;

  public static final int VALUE_INSET = 8;

  private GradleEditorUiConstants() {
  }
}
