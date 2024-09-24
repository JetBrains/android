/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer;
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.AnalyzerStatus;
import com.intellij.openapi.editor.markup.InspectionsLevel;
import com.intellij.openapi.editor.markup.LanguageHighlightLevel;
import com.intellij.openapi.editor.markup.UIController;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.GridBag;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A custom renderer to display analysis information when query sync is enabled and the dependencies
 * of a file have not been built.
 */
public class QuerySyncTrafficLightRendererContributor implements TrafficLightRendererContributor {

  @Override
  @Nullable
  public TrafficLightRenderer createRenderer(@NotNull Editor editor, @Nullable PsiFile psiFile) {
    if (Blaze.getProjectType(psiFile.getProject()) != ProjectType.QUERY_SYNC) {
      return null;
    }
    return new TrafficLightRenderer(psiFile.getProject(), editor.getDocument()) {
      @Override
      @NotNull
      public AnalyzerStatus getStatus() {
        if (QuerySyncManager.getInstance(psiFile.getProject()).isReadyForAnalysis(psiFile)) {
          return super.getStatus();
        }
        return new AnalyzerStatus(
            AllIcons.Debugger.Db_invalid_breakpoint,
            "Analysis is disabled",
            "Analysis is disabled until all the dependencies of this file are built",
            this::createCustomController);
      }

      @Override
      @NotNull
      protected UIController createUIController() {
        return super.createUIController(editor);
      }

      @NotNull
      protected UIController createCustomController() {
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
    };
  }
}
