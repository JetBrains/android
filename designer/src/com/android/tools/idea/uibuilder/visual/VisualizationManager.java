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

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.util.Arrays;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Manages a shared visualization window on the right side of the source editor which shows a preview
 * of the focused Android layout file. When user is not interacting with Android layout file then
 * the window is gone.
 * <p>
 * The visualization tool use {@link NlDesignSurface} for rendering previews.
 */
public class VisualizationManager implements Disposable {

  // Must be same as the tool window id in designer.xml
  @NotNull public static final String TOOL_WINDOW_ID = "Layout Validation";

  /**
   * The default width for first time open.
   */
  private static final int DEFAULT_WINDOW_WIDTH = 500;

  @Nullable
  private final MergingUpdateQueue myToolWindowUpdateQueue;

  @NotNull private final Project myProject;

  @Nullable private VisualizationForm myToolWindowForm;
  @Nullable private final ToolWindow myToolWindow;

  public VisualizationManager(@NotNull Project project) {
    myProject = project;
    myToolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
    if (myToolWindow == null) {
      Logger.getInstance(getClass()).error("Cannot find Tool Window of Layout Validation Tool");
      myToolWindowUpdateQueue = null;
      return;
    }
    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.visual", 100, true, null, this);
    // TODO(b/180927397): Move File Editor Listener into VisualizationToolFactory
    final MessageBusConnection connection = myProject.getMessageBus().connect(this);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());

    processFileEditorChange(FileEditorManager.getInstance(myProject).getSelectedEditor());
  }

  @VisibleForTesting
  public boolean isWindowVisible() {
    return myToolWindow != null && myToolWindow.isVisible();
  }

  @TestOnly
  public boolean isToolWindowAvailable() {
    return myToolWindow != null && myToolWindow.isAvailable();
  }

  @NotNull
  private VisualizationForm initToolWindowContent(@NotNull ToolWindow toolWindow) {
    // TODO(b/180927397): move tool initialization to VisualizationToolFactory
    VisualizationForm visualizationForm = new VisualizationForm(myProject, VisualizationManager.this);

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

  private void processFileEditorChange(@Nullable final FileEditor newEditor) {
    if (myToolWindowUpdateQueue == null) {
      return;
    }
    assert myToolWindow != null : "The update queue only exists when tool window is not null";

    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (myToolWindow.isDisposed()) {
          return;
        }

        if (myToolWindowForm == null) {
          myToolWindowForm = initToolWindowContent(myToolWindow);

          myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
            @Override
            public void stateChanged(@NotNull ToolWindowManager manager) {
              if (myProject.isDisposed()) {
                return;
              }
              if (VisualizationToolSettings.getInstance().getGlobalState().isFirstTimeOpen() && myToolWindow instanceof ToolWindowEx) {
                ToolWindowEx windowEx = (ToolWindowEx)myToolWindow;
                int width = myToolWindow.getComponent().getWidth();
                windowEx.stretchWidth(DEFAULT_WINDOW_WIDTH - width);
              }
              VisualizationToolSettings.getInstance().getGlobalState().setFirstTimeOpen(false);
              if (myToolWindow.isAvailable()) {
                final boolean visible = myToolWindow.isVisible();
                VisualizationToolSettings.getInstance().getGlobalState().setVisible(visible);

                if (myToolWindowForm != null) {
                  if (visible) {
                    myToolWindowForm.activate();
                  }
                  else {
                    myToolWindowForm.deactivate();
                  }
                }
              }
            }
          });
        }

        if (newEditor == null || !myToolWindowForm.setNextEditor(newEditor)) {
          myToolWindow.setAvailable(false);
          return;
        }

        myToolWindow.setAvailable(true);
        if (VisualizationToolSettings.getInstance().getGlobalState().isVisible() && !myToolWindow.isVisible()) {
          Runnable restoreFocus = null;
          if (myToolWindow.getType() == ToolWindowType.WINDOWED) {
            // Ugly hack: Fix for b/68148499
            // We never want the preview to take focus when the content of the preview changes because of a file change.
            // Even when the preview is restored after being closed (move from Java file to an XML file).
            // There is no way to show the tool window without also taking the focus.
            // This hack is a workaround that sets the focus back to editor.
            // Note, that this may be wrong in certain circumstances, but should be OK for most scenarios.
            restoreFocus = () -> IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> restoreFocusToEditor(newEditor));
          }
          myToolWindow.activate(restoreFocus, false, false);
        }
      }
    });
  }

  @Override
  public void dispose() {
    if (myToolWindowForm != null) {
      Disposer.dispose(myToolWindowForm);
      myToolWindowForm = null;
    }
  }

  private static void restoreFocusToEditor(@NotNull FileEditor newEditor) {
    ApplicationManager.getApplication().invokeLater(() -> newEditor.getComponent().requestFocus());
  }

  /**
   * Find an active editor for the specified file, or just the first active editor if file is null.
   */
  @Nullable
  private FileEditor getActiveLayoutEditor(@Nullable PsiFile file) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<FileEditor>)() -> getActiveLayoutEditor(file));
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return Arrays.stream(FileEditorManager.getInstance(myProject).getSelectedEditors())
      .filter(editor -> {
        VirtualFile editorFile = editor.getFile();
        return editorFile != null && editorFile.equals(file);
      })
      .findFirst()
      .orElse(null);
  }

  @Nullable
  public static VisualizationManager getInstance(Project project) {
    return project.getService(VisualizationManager.class);
  }

  @VisibleForTesting
  @Nullable
  public MergingUpdateQueue getToolWindowUpdateQueue() {
    return myToolWindowUpdateQueue;
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (!file.isValid()) {
        return;
      }

      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
      FileEditor fileEditor = getActiveLayoutEditor(psiFile);
      if (fileEditor != null) {
        processFileEditorChange(fileEditor);
      }
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      if (myToolWindowForm != null) {
        // Remove stale references from the preview form. See b/80084773
        myToolWindowForm.fileClosed(source, file);
      }
      // When using "Close All" action, the selectionChanged event is not triggered.
      // Thus we have to handle this case here.
      // In other cases, do not respond to fileClosed events since this has led to problems
      // with the preview window in the past. See b/64199946 and b/64288544
      if (source.getOpenFiles().length == 0) {
        processFileEditorChange(null);
      }
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      FileEditor editorForLayout = null;
      FileEditor newEditor = event.getNewEditor();
      if (newEditor != null) {
        VirtualFile newVirtualFile = newEditor.getFile();
        if (newVirtualFile != null) {
          PsiFile psiFile = PsiManager.getInstance(myProject).findFile(newVirtualFile);
          if (IdeResourcesUtil.getFolderType(psiFile) == ResourceFolderType.LAYOUT) {
            // Visualization tool only works for layout files.
            editorForLayout = newEditor;
          }
        }
      }
      processFileEditorChange(editorForLayout);
    }
  }
}
