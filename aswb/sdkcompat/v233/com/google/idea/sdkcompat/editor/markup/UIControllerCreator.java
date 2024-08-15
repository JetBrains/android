/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.editor.markup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.InspectionsLevel;
import com.intellij.openapi.editor.markup.LanguageHighlightLevel;
import com.intellij.openapi.editor.markup.UIController;
import com.intellij.util.ui.GridBag;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

/** A compat class help to create UIController instance for different sdk */
public final class UIControllerCreator {
  public static UIController create() {
    return new UIController() {
      @Override
      public void toggleProblemsView() {}

      @Override
      public void setHighLightLevel(LanguageHighlightLevel level) {}

      @Override
      public void onClosePopup() {}

      @Override
      public List<LanguageHighlightLevel> getHighlightLevels() {
        return new ArrayList<>();
      }

      @Override
      public List<InspectionsLevel> getAvailableLevels() {
        return new ArrayList<>();
      }

      @Override
      public List<AnAction> getActions() {
        return new ArrayList<>();
      }

      @Override
      public void fillHectorPanels(Container container, GridBag bag) {}

      @Override
      public boolean canClosePopup() {
        return true;
      }

      @Override
      public boolean isToolbarEnabled() {
        return true;
      }
    };
  }

  private UIControllerCreator() {}
}
