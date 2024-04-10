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

  /**
   * The default mode of the editor that can be chosen when opening a file for the first time.
   * Possible choices are:
   * - Code: It shows only an editor with the code of the file.
   * - Design: It shows only a preview of the render result of the file.
   * - Split: It shows a split screen with both Code and Design.
   */
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
   * The different layouts to organise the way to show previews:
   * - List: organises the previews in a vertical oriented list.
   * - Grid: organises the previews into a grid.
   * - Gallery: shows one single preview at a time.
   */
  public enum LayoutType {
    LIST("List"),
    GRID("Grid"),
    GALLERY("Gallery");

    @NotNull
    private final String myDisplayName;

    LayoutType(@NotNull String displayName){
      myDisplayName = displayName;
    }

    @NotNull
    public String getDisplayName(){
      return myDisplayName;
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
    private EditorMode myPreferredResourcesEditorMode;
    private EditorMode myPreferredKotlinEditorMode;
    private LayoutType myPreferredLayoutType;
    private double myMagnifySensitivity = DEFAULT_MAGNIFY_SENSITIVITY;
    private boolean myPreviewEssentialsModeEnabled = false;

    /**
     * When true, it always shows split mode if file contains a @Preview annotation.
     */
    private boolean myShowSplitViewForPreviewFiles = true;

    public EditorMode getPreferredEditorMode() {
      return myPreferredEditorMode;
    }

    public void setPreferredEditorMode(EditorMode preferredEditorMode) {
      myPreferredEditorMode = preferredEditorMode;
    }

    public EditorMode getPreferredResourcesEditorMode() {
      return myPreferredResourcesEditorMode;
    }

    public void setPreferredResourcesEditorMode(EditorMode preferredResourcesEditorMode) {
      myPreferredResourcesEditorMode = preferredResourcesEditorMode;
    }

    public boolean getShowSplitViewForPreviewFiles() {
      return myShowSplitViewForPreviewFiles;
    }

    public void setShowSplitViewForPreviewFiles(boolean showSplitViewForPreviewFiles) {
      myShowSplitViewForPreviewFiles = showSplitViewForPreviewFiles;
    }

    public EditorMode getPreferredKotlinEditorMode() {
      return myPreferredKotlinEditorMode;
    }

    public void setPreferredKotlinEditorMode(EditorMode preferredKotlinEditorMode) {
      myPreferredKotlinEditorMode = preferredKotlinEditorMode;
    }

    public void setPreferredPreviewLayoutMode(LayoutType preferredLayoutType) {
      myPreferredLayoutType = preferredLayoutType;
    }

    public LayoutType getPreferredPreviewLayoutMode() {
      return myPreferredLayoutType;
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
