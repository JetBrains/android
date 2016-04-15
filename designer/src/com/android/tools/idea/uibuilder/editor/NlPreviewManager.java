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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.palette.ScalableDesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.designer.LightToolWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * Manages a shared UI Preview window on the right side of the source editor which shows a preview
 * of the corresponding Android resource content on the left.
 * <p>
 * Based on the earlier {@link AndroidLayoutPreviewToolWindowManager} but updated to use
 * (a) the {@link ResourceNotificationManager} for update tracking, and (b) the
 * {@link DesignSurface} for layout rendering and direct manipulation editing.
 */
public class NlPreviewManager implements ProjectComponent {
  private final MergingUpdateQueue myToolWindowUpdateQueue;

  private final Project myProject;
  private final FileEditorManager myFileEditorManager;

  private NlPreviewForm myToolWindowForm;
  private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;

  public NlPreviewManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.preview", 100, true, null, project);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        myToolWindowReady = true;
        processFileEditorChange(getActiveLayoutXmlEditor());
      }
    });
  }

  protected boolean isUseInteractiveSelector() {
    return true;
  }

  protected String getToolWindowId() {
    return AndroidBundle.message("android.layout.preview.tool.window.title");
  }

  protected void initToolWindow() {
    myToolWindowForm = new NlPreviewForm(this);
    final String toolWindowId = getToolWindowId();
    myToolWindow =
      ToolWindowManager.getInstance(myProject).registerToolWindow(toolWindowId, false, ToolWindowAnchor.RIGHT, myProject, true);
    myToolWindow.setIcon(AndroidIcons.AndroidPreview);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      @Override
      public void stateChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
        if (window != null && window.isAvailable()) {
          final boolean visible = window.isVisible();
          AndroidEditorSettings.getInstance().getGlobalState().setVisible(visible);

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

    final JPanel contentPanel = myToolWindowForm.getContentPanel();
    final ContentManager contentManager = myToolWindow.getContentManager();
    @SuppressWarnings("ConstantConditions") final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(myToolWindowForm);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
    myToolWindowForm.setUseInteractiveSelector(isUseInteractiveSelector());
  }

  @Override
  public void projectClosed() {
    if (myToolWindowForm != null) {
      Disposer.dispose(myToolWindowForm);
      NlPaletteManager paletteManager = NlPaletteManager.get(myProject);
      String paletteKey = paletteManager.getComponentName();
      LightToolWindow toolWindow = (LightToolWindow)myToolWindowForm.getClientProperty(paletteKey);
      if (toolWindow != null) {
        myToolWindowForm.putClientProperty(paletteKey, null);
        toolWindow.dispose();
      }
      myToolWindowForm = null;
      myToolWindow = null;
      myToolWindowDisposed = true;
    }
  }

  // The preview image was updated. Notify the attached palette if any.
  public void setDesignSurface(@Nullable ScalableDesignSurface designSurface) {
    if (myToolWindow != null) {
      NlPaletteManager paletteManager = NlPaletteManager.get(myProject);
      LightToolWindow toolWindow = (LightToolWindow)myToolWindowForm.getClientProperty(paletteManager.getComponentName());
      if (toolWindow != null && toolWindow.isVisible()) {
        paletteManager.setDesignSurface(toolWindow, designSurface);
      }
    }
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "NlPreviewManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
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

  private boolean myRenderImmediately;

  private void processFileEditorChange(@Nullable final TextEditor newEditor) {
    if (!RenderService.NELE_ENABLED) {
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
        boolean renderImmediately = myRenderImmediately;
        myRenderImmediately = false;

        final Editor activeEditor = newEditor != null ? newEditor.getEditor() : null;

        if (myToolWindow == null) {
          if (activeEditor == null) {
            return;
          }
          else if (!activeEditor.getComponent().isShowing()) {
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
              myPendingShowComponent = activeEditor.getComponent();
              if (myHierarchyListener == null) {
                myHierarchyListener = new HierarchyListener() {
                  @Override
                  public void hierarchyChanged(HierarchyEvent hierarchyEvent) {
                    if ((hierarchyEvent.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                      if (hierarchyEvent.getComponent() == myPendingShowComponent && myPendingShowComponent.isShowing()) {
                        myPendingShowComponent.removeHierarchyListener(myHierarchyListener);
                        mySeenEditor = true;
                        myPendingShowComponent = null;
                        processFileEditorChange(getActiveLayoutXmlEditor());
                      }
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

        final AndroidEditorSettings settings = AndroidEditorSettings.getInstance();
        final boolean hideForNonLayoutFiles = settings.getGlobalState().isHideForNonLayoutFiles();

        if (activeEditor == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(activeEditor.getDocument());
        myToolWindowForm.setFile(psiFile);
        if (psiFile == null) {
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        myToolWindow.setAvailable(true, null);
        final boolean visible = AndroidEditorSettings.getInstance().getGlobalState().isVisible();
        if (visible) {
          // Clear out the render result for the previous file, such that it doesn't briefly show between the time the
          // tool window is shown and the time the render has completed
          if (!myToolWindow.isVisible()) {
            RenderResult renderResult = myToolWindowForm.getRenderResult();
            if (renderResult != null && renderResult.getFile() != psiFile) {
              myToolWindowForm.setRenderResult(RenderResult.createBlank(psiFile, null));
            }
          }
          myToolWindow.show(null);
        }
      }
    });
  }

  @Nullable
  TextEditor getActiveLayoutXmlEditor() {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction(new Computable<TextEditor>() {
        @Override
        public TextEditor compute() {
          return getActiveLayoutXmlEditor();
        }
      });
    }
    ApplicationManager.getApplication().assertReadAccessAllowed();
    FileEditor[] fileEditors = myFileEditorManager.getSelectedEditors();
    if (fileEditors.length > 0 && fileEditors[0] instanceof TextEditor) {
      final TextEditor textEditor = (TextEditor)fileEditors[0];
      if (isApplicableEditor(textEditor)) {
        return textEditor;
      }
    }
    return null;
  }

  protected boolean isApplicableEditor(TextEditor textEditor) {
    final Document document = textEditor.getEditor().getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

    // In theory, we should just check
    //   LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
    // here, but there are problems where files don't show up with layout preview
    // at startup, presumably because the resource directories haven't been properly
    // initialized yet.
    return isInResourceFolder(psiFile);
  }

  @NotNull
  public NlPreviewForm getPreviewForm() {
    if (myToolWindow == null) {
      initToolWindow();
    }
    return myToolWindowForm;
  }

  private static boolean isInResourceFolder(@Nullable PsiFile psiFile) {
    if (psiFile instanceof XmlFile && AndroidFacet.getInstance(psiFile) != null) {
      return RenderService.canRender(psiFile);
    }
    return false;
  }

  public static NlPreviewManager getInstance(Project project) {
    return project.getComponent(NlPreviewManager.class);
  }

  /**
   * Manually notify the manager that an editor is about to be shown; typically done right after
   * switching to a file to show an update as soon as possible. This is used when we know
   * the editor is about to be shown (because we've requested it). We don't have a way to
   * add a listener which is called after the requested file has been opened, so instead we
   * simply anticipate the change by calling this method first; the subsequent file open will
   * then become a no-op since the file doesn't change.
   */
  public void notifyFileShown(@NotNull TextEditor editor, boolean renderImmediately) {
    // Don't delete: should be invoked from ConfigurationAction#pickedBetterMatch when we can access designer code from there
    // (or when ConfigurationAction moves here)
    if (renderImmediately) {
      myRenderImmediately = true;
    }
    processFileEditorChange(editor);
    if (renderImmediately) {
      myToolWindowUpdateQueue.sendFlush();
    }
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      processFileEditorChange(getActiveLayoutXmlEditor());
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          processFileEditorChange(getActiveLayoutXmlEditor());
        }
      }, myProject.getDisposed());
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      final FileEditor newEditor = event.getNewEditor();
      TextEditor layoutXmlEditor = null;
      if (newEditor instanceof TextEditor) {
        final TextEditor textEditor = (TextEditor)newEditor;
        if (isApplicableEditor(textEditor)) {
          layoutXmlEditor = textEditor;
        }
      }
      processFileEditorChange(layoutXmlEditor);
    }
  }
}
