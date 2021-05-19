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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
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
import icons.StudioIcons;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Arrays;
import javax.swing.*;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a shared visualization window on the right side of the source editor which shows a preview
 * of the focused Android layout file. When user is not interacting with Android layout file then
 * the window is gone.
 * <p>
 * The visualization tool use {@link NlDesignSurface} for rendering previews.
 */
public class VisualizationManager implements Disposable {
  /**
   * The default width for first time open.
   */
  private static final int DEFAULT_WINDOW_WIDTH = 500;

  @Nullable private final MergingUpdateQueue myToolWindowUpdateQueue;

  private final Project myProject;

  @Nullable private VisualizationForm myToolWindowForm;
  @Nullable private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;

  public static class VisualizationManagerPostStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      getInstance(project).onToolWindowReady();
    }
  }

  public VisualizationManager(@NotNull Project project) {
    myProject = project;

    if (!StudioFlags.NELE_VISUALIZATION.get()) {
      myToolWindowUpdateQueue = null;
      return;
    }
    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.visual", 100, true, null, project);
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  private void onToolWindowReady(){
    myToolWindowReady = true;
    processFileEditorChange(FileEditorManager.getInstance(myProject).getSelectedEditor());
  }

  public boolean isWindowVisible() {
    return myToolWindow != null && myToolWindow.isVisible();
  }

  @NotNull
  public String getToolWindowId() {
      return AndroidBundle.message("android.layout.visual.tool.window.title");
  }

  @NotNull
  private VisualizationForm createPreviewForm() {
    return new VisualizationForm(myProject);
  }

  protected void initToolWindow() {
    if (!StudioFlags.NELE_VISUALIZATION.get()) {
      return;
    }
    myToolWindowForm = createPreviewForm();
    Disposer.register(this, myToolWindowForm);

    final String toolWindowId = getToolWindowId();
    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(RegisterToolWindowTask.notClosable(toolWindowId, ToolWindowAnchor.RIGHT));
    myToolWindow.setIcon(StudioIcons.Shell.ToolWindows.MULTI_PREVIEW);

    myProject.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      @Override
      public void stateChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
        if (VisualizationToolSettings.getInstance().getGlobalState().isFirstTimeOpen() && window instanceof ToolWindowEx) {
          ToolWindowEx windowEx = (ToolWindowEx)window;
          int width = window.getComponent().getWidth();
          windowEx.stretchWidth(DEFAULT_WINDOW_WIDTH - width);
        }
        VisualizationToolSettings.getInstance().getGlobalState().setFirstTimeOpen(false);
        if (window != null && window.isAvailable()) {
          final boolean visible = window.isVisible();
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

    final JComponent contentPanel = myToolWindowForm.getComponent();
    final ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.addDataProvider(dataId -> {
      FileEditor fileEditor = myToolWindowForm.getEditor();
      if (fileEditor == null) return null;
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return fileEditor.getFile();
      }
      else if (LangDataKeys.IDE_VIEW.is(dataId)) {
        return ((DataProvider)fileEditor).getData(dataId);
      }
      else if (LangDataKeys.MODULE.is(dataId)) {
        VirtualFile file = fileEditor.getFile();
        return file == null ? null : ModuleUtilCore.findModuleForFile(file, myProject);
      }
      return null;
    });

    final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(myToolWindowForm);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    if (isWindowVisible()) {
      myToolWindowForm.activate();
    }
  }

  /**
   * Whether we've seen an open file editor yet
   */
  private boolean mySeenEditor;

  /**
   * The most recently opened file editor that was not showing (while {@link #mySeenEditor} was false)
   */
  private JComponent myPendingShowComponent;

  /**
   * A listener on {@link #myPendingShowComponent} which listens for the most recently opened file editor to start showing
   */
  private HierarchyListener myHierarchyListener;

  private void processFileEditorChange(@Nullable final FileEditor newEditor) {
    if (myToolWindowUpdateQueue == null) {
      return;
    }
    if (myPendingShowComponent != null) {
      myPendingShowComponent.removeHierarchyListener(myHierarchyListener);
      myPendingShowComponent = null;
    }

    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        if (myToolWindow == null) {
          if (newEditor == null) {
            return;
          }
          else if (!newEditor.getComponent().isShowing()) {
            // When the IDE starts, it opens all the previously open editors, one
            // after the other. This means that this method gets called, and for
            // each layout editor that is on top, it opens up the preview window
            // and starts a render, even if the topmost editor is not a layout
            // editor file. However, unlike a normal tab switch performed by the
            // user, we can detect the startup scenario by ignoring editors that
            // are not actually showing, so if editor tabs aren't showing, we ignore
            // them.
            //
            // However, it's possible for the last editor to come up and not be
            // marked showing yet. That means that the XML editor comes up and
            // you have to give it focus before the layout preview kicks in.
            // The reason this happens is that the last event we receive is when
            // the file is opened (but the editor is not yet showing).
            // To deal with this, the following code adds a hierarchy listener,
            // which is notified when the component associated with this editor
            // is actually shown. We need to remove those listeners as soon
            // as we switch to a different editor (which at startup happens rapidly
            // for each successive restored editor tab). And we only do this
            // at startup (recorded by the mySeenEditor field; this is startup
            // per project frame.)
            if (!mySeenEditor) {
              myPendingShowComponent = newEditor.getComponent();
              if (myHierarchyListener == null) {
                myHierarchyListener = hierarchyEvent -> {
                  if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (hierarchyEvent.getComponent() == myPendingShowComponent && myPendingShowComponent.isShowing()) {
                      myPendingShowComponent.removeHierarchyListener(myHierarchyListener);
                      mySeenEditor = true;
                      myPendingShowComponent = null;
                      processFileEditorChange(getFirstActiveLayoutEditor());
                    }
                  }
                };
              }
              myPendingShowComponent.addHierarchyListener(myHierarchyListener);
            }

            return;
          }
          mySeenEditor = true;
          initToolWindow();
        }

        if (myToolWindow == null || myToolWindowForm == null) {
          return;
        }

        if (newEditor == null) {
          myToolWindow.setAvailable(false);
          return;
        }

        if (!myToolWindowForm.setNextEditor(newEditor)) {
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
      myToolWindow = null;
      myToolWindowDisposed = true;
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

  /**
   * Find an active editor for the specified file, or just the first active editor if file is null.
   */
  @Nullable
  private FileEditor getFirstActiveLayoutEditor() {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<FileEditor>)() -> getFirstActiveLayoutEditor());
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    return Arrays.stream(FileEditorManager.getInstance(myProject).getSelectedEditors())
      .filter(editor -> {
        VirtualFile editorFile = editor.getFile();
        ResourceFolderType type = IdeResourcesUtil.getFolderType(editorFile);
        return type == ResourceFolderType.LAYOUT;
      })
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private ToolWindow getToolWindow() {
    return myToolWindow;
  }

  public static VisualizationManager getInstance(Project project) {
    return project.getService(VisualizationManager.class);
  }

  @NotNull
  public Project getProject() {
    return myProject;
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
