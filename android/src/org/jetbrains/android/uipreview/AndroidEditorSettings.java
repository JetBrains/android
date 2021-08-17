// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    private boolean myVisible = true;
    private boolean myHideForNonLayoutFiles = true;
    private boolean myShowDeviceFrames = true;
    private boolean myShowEffects = true;
    private boolean myRetina = true;
    private boolean myPreferXmlEditor = false;
    private boolean myShowLint = false;
    private EditorMode myPreferredEditorMode;
    private EditorMode myPreferredDrawableEditorMode;
    private EditorMode myPreferredComposableEditorMode;
    private EditorMode myPreferredKotlinEditorMode;

    public boolean isRetina() {
      return myRetina;
    }

    public void setRetina(boolean retina) {
      myRetina = retina;
    }

    public boolean isVisible() {
      return myVisible;
    }

    public void setVisible(boolean visible) {
      myVisible = visible;
    }

    public boolean isHideForNonLayoutFiles() {
      return myHideForNonLayoutFiles;
    }

    public void setHideForNonLayoutFiles(boolean hideForNonLayoutFiles) {
      myHideForNonLayoutFiles = hideForNonLayoutFiles;
    }

    public boolean isShowDeviceFrames() {
      return myShowDeviceFrames;
    }

    public void setShowDeviceFrames(boolean showDeviceFrames) {
      myShowDeviceFrames = showDeviceFrames;
    }

    public boolean isShowEffects() {
      return myShowEffects;
    }

    public void setShowEffects(boolean showEffects) {
      myShowEffects = showEffects;
    }

    public boolean isPreferXmlEditor() {
      return myPreferXmlEditor;
    }

    public void setShowLint(boolean showLint) {
      myShowLint = showLint;
    }

    public boolean isShowLint() {
      return myShowLint;
    }

    public void setPreferXmlEditor(boolean preferXmlEditor) {
      myPreferXmlEditor = preferXmlEditor;
    }

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

    public EditorMode getPreferredKotlinEditorMode() {
      return myPreferredKotlinEditorMode;
    }

    public void setPreferredKotlinEditorMode(EditorMode preferredKotlinEditorMode) {
      myPreferredKotlinEditorMode = preferredKotlinEditorMode;
    }
  }
}
