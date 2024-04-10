/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

@State(name = "AndroidEditors", storages = @Storage("androidEditors.xml"))
public class AndroidEditorSettings implements PersistentStateComponent<AndroidEditorSettings.MyState> {
  public enum EditorMode {
    CODE("Code", AllIcons.General.LayoutEditorOnly),
    SPLIT("Split", AllIcons.General.LayoutEditorPreview),
    DESIGN("Design", AllIcons.General.LayoutPreviewOnly);

    @NotNull
    private final String myDisplayName;

    @NotNull
    private final Icon myIcon;

    EditorMode(@NotNull String displayName, @NotNull Icon icon) {
      myDisplayName = displayName;
      myIcon = icon;
    }

    @NotNull
    public String getDisplayName() {
      return myDisplayName;
    }

    @NotNull
    public Icon getIcon() {
      return myIcon;
    }
  }

  /**
   * The minimum magnify sensitivity value. Can't be zero. Otherwise the magnify function is disabled.
   */
  public static final double MIN_MAGNIFY_SENSITIVITY = 0.1;
  /**
   * The maximum magnify sensitivity value.
   */
  public static final double MAX_MAGNIFY_SENSITIVITY = 2.1;
  /**
   * The default value of magnify sensitivity.
   */
  public static final double DEFAULT_MAGNIFY_SENSITIVITY = (MIN_MAGNIFY_SENSITIVITY + MAX_MAGNIFY_SENSITIVITY) / 2;

  private GlobalState myGlobalState = new GlobalState();

  public static AndroidEditorSettings getInstance() {
    return ApplicationManager.getApplication().getService(AndroidEditorSettings.class);
  }

  @NotNull
  public GlobalState getGlobalState() {
    return myGlobalState;
  }

  @Override
  public MyState getState() {
    final MyState state = new MyState();
    state.setState(myGlobalState);
    return state;
  }

  @Override
  public void loadState(@NotNull MyState state) {
    myGlobalState = state.getState();
  }

  public static class MyState {
    private GlobalState myGlobalState = new GlobalState();

    public GlobalState getState() {
      return myGlobalState;
    }

    public void setState(GlobalState state) {
      myGlobalState = state;
    }
  }

  public static class GlobalState {
    private EditorMode myPreferredEditorMode;
    private EditorMode myPreferredDrawableEditorMode;
    private EditorMode myPreferredComposableEditorMode;
    private EditorMode myPreferredPreviewableEditorMode;
    private EditorMode myPreferredKotlinEditorMode;
    private double myMagnifySensitivity = DEFAULT_MAGNIFY_SENSITIVITY;
    private boolean myPreviewEssentialsModeEnabled = false;

    public EditorMode getPreferredEditorMode() {
      return myPreferredEditorMode;
    }

    public void setPreferredEditorMode(EditorMode preferredEditorMode) {
      myPreferredEditorMode = preferredEditorMode;
    }

    public EditorMode getPreferredDrawableEditorMode() {
      return myPreferredDrawableEditorMode;
    }

    public void setPreferredDrawableEditorMode(EditorMode preferredDrawableEditorMode) {
      myPreferredDrawableEditorMode = preferredDrawableEditorMode;
    }

    public EditorMode getPreferredComposableEditorMode() {
      return myPreferredComposableEditorMode;
    }

    public void setPreferredComposableEditorMode(EditorMode preferredComposableEditorMode) {
      myPreferredComposableEditorMode = preferredComposableEditorMode;
    }

    public EditorMode getPreferredPreviewableEditorMode() {
      return myPreferredPreviewableEditorMode;
    }

    public void setPreferredPreviewableEditorMode(EditorMode preferredPreviewableEditorMode) {
      myPreferredPreviewableEditorMode = preferredPreviewableEditorMode;
    }

    public EditorMode getPreferredKotlinEditorMode() {
      return myPreferredKotlinEditorMode;
    }

    public void setPreferredKotlinEditorMode(EditorMode preferredKotlinEditorMode) {
      myPreferredKotlinEditorMode = preferredKotlinEditorMode;
    }

    public double getMagnifySensitivity() {
      return myMagnifySensitivity;
    }

    public void setMagnifySensitivity(double magnifySensitivity) {
      myMagnifySensitivity = magnifySensitivity;
    }

    public boolean isPreviewEssentialsModeEnabled() {
      return myPreviewEssentialsModeEnabled;
    }

    public void setPreviewEssentialsModeEnabled(boolean previewEssentialsModeEnabled) {
      myPreviewEssentialsModeEnabled = previewEssentialsModeEnabled;
    }
  }
}
