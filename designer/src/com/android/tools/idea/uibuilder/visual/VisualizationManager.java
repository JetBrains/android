/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual;

import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Manages a shared visualization window on the right side of the source editor which shows a preview
 * of the focused Android layout file. When user is not interacting with Android layout file then
 * the window is gone.
 * <p>
 * The visualization tool use {@link NlDesignSurface} for rendering previews.
 */
public class VisualizationManager {

  // Must be same as the tool window id in designer.xml
  @NotNull public static final String TOOL_WINDOW_ID = "Layout Validation";

  /**
   * The default width for first time open.
   */
  private static final int DEFAULT_WINDOW_WIDTH = 500;

  @NotNull private final Project myProject;
  @NotNull private final MergingUpdateQueue myToolWindowUpdateQueue;
  @Nullable private VisualizationForm myToolWindowForm;

  public VisualizationManager(@NotNull Project project) {
    myProject = project;
    // TODO(b/180927397): The disposable parent of this queue can be ToolWindow.
    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.visual", 100, true, null, null);
  }

  @NotNull
  private VisualizationForm initToolWindowContent(@NotNull ToolWindow toolWindow) {
    // TODO(b/180927397): move tool initialization to VisualizationToolFactory if possible?
    VisualizationForm visualizationForm = new VisualizationForm(myProject, toolWindow.getDisposable());

    final JComponent contentPanel = visualizationForm.getComponent();
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addDataProvider(dataId -> {
      if (LangDataKeys.MODULE.is(dataId) || LangDataKeys.IDE_VIEW.is(dataId) || CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        FileEditor fileEditor = visualizationForm.getEditor();
        if (fileEditor != null) {
          JComponent component = fileEditor.getComponent();
          DataContext context = DataManager.getInstance().getDataContext(component);
          return context.getData(dataId);
        }
      }
      return null;
    });

    final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(visualizationForm);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    if (toolWindow.isVisible()) {
      visualizationForm.activate();
    }
    return visualizationForm;
  }

  public void processFileEditorChange(@Nullable final FileEditor newEditor, @NotNull ToolWindow toolWindow) {
    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (toolWindow.isDisposed()) {
          return;
        }

        if (myToolWindowForm == null) {
          final VisualizationForm form = initToolWindowContent(toolWindow);
          myToolWindowForm = form;

          myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(@NotNull ToolWindowManager manager) {
              if (myProject.isDisposed()) {
                return;
              }
              if (VisualizationToolSettings.getInstance().getGlobalState().isFirstTimeOpen() && toolWindow instanceof ToolWindowEx) {
                ToolWindowEx windowEx = (ToolWindowEx)toolWindow;
                int width = toolWindow.getComponent().getWidth();
                windowEx.stretchWidth(DEFAULT_WINDOW_WIDTH - width);
              }
              VisualizationToolSettings.getInstance().getGlobalState().setFirstTimeOpen(false);
              if (toolWindow.isAvailable()) {
                final boolean visible = toolWindow.isVisible();
                VisualizationToolSettings.getInstance().getGlobalState().setVisible(visible);

                if (!Disposer.isDisposed(form)) {
                  if (visible) {
                    form.activate();
                  }
                  else {
                    form.deactivate();
                  }
                }
              }
            }
          });
        }
        if (Disposer.isDisposed(myToolWindowForm)) {
          return;
        }

        if (newEditor == null || !myToolWindowForm.setNextEditor(newEditor)) {
          toolWindow.setAvailable(false);
          return;
        }

        toolWindow.setAvailable(true);
        if (VisualizationToolSettings.getInstance().getGlobalState().isVisible() && !toolWindow.isVisible()) {
          Runnable restoreFocus = null;
          if (toolWindow.getType() == ToolWindowType.WINDOWED) {
            // Ugly hack: Fix for b/68148499
            // We never want the preview to take focus when the content of the preview changes because of a file change.
            // Even when the preview is restored after being closed (move from Java file to an XML file).
            // There is no way to show the tool window without also taking the focus.
            // This hack is a workaround that sets the focus back to editor.
            // Note, that this may be wrong in certain circumstances, but should be OK for most scenarios.
            restoreFocus = () -> IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> restoreFocusToEditor(newEditor));
          }
          toolWindow.activate(restoreFocus, false, false);
        }
      }
    });
  }

  public void processFileClose(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    if (myToolWindowForm != null && !Disposer.isDisposed(myToolWindowForm)) {
      // Remove stale references from the preview form. See b/80084773
      myToolWindowForm.fileClosed(source, file);
    }
  }

  private static void restoreFocusToEditor(@NotNull FileEditor newEditor) {
    ApplicationManager.getApplication().invokeLater(() -> newEditor.getComponent().requestFocus());
  }

  @Nullable
  public static VisualizationManager getInstance(Project project) {
    return project.getService(VisualizationManager.class);
  }

  @VisibleForTesting
  @NotNull
  public MergingUpdateQueue getToolWindowUpdateQueue() {
    return myToolWindowUpdateQueue;
  }
}
