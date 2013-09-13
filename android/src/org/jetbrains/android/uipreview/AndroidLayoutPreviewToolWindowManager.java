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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.ProjectTopics;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import icons.AndroidIcons;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewToolWindowManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager");

  private final MergingUpdateQueue myToolWindowUpdateQueue;

  private final Object myRenderingQueueLock = new Object();
  private MergingUpdateQueue myRenderingQueue;

  private final MergingUpdateQueue myRenderQueue;

  private final Project myProject;
  private final FileEditorManager myFileEditorManager;

  private AndroidLayoutPreviewToolWindowForm myToolWindowForm;
  private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;
  private final VirtualFileListener myListener = new MyVirtualFileListener();

  private static final Object RENDERING_LOCK = new Object();

  public AndroidLayoutPreviewToolWindowManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.preview", 300, true, null, project);
    myRenderQueue = new MergingUpdateQueue("android.layout.preview.save.and.render", 1000, true, null, project, null, true);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener(project));

    LocalFileSystem.getInstance().addVirtualFileListener(myListener);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        LocalFileSystem.getInstance().removeVirtualFileListener(myListener);
      }
    });

    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        update(event);
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        update(event);
      }

      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        update(event);
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        update(event);
      }
    }, project);

    CompilerManager.getInstance(project).addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        if (myToolWindowForm != null &&
            myToolWindowReady &&
            !myToolWindowDisposed) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              render();
            }
          });
        }
        return true;
      }
    });
  }

  @NotNull
  private MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", 800, true, null,
                                                  myProject, null, Alarm.ThreadToUse.OWN_THREAD);
      }
      return myRenderingQueue;
    }
  }

  private void update(PsiTreeChangeEvent event) {
    if (myToolWindowForm == null || !myToolWindowReady || myToolWindowDisposed) {
      return;
    }
    final PsiFile fileInPreview = myToolWindowForm.getFile();
    final PsiFile file = event.getFile();

    if (fileInPreview == null || file == null) {
      return;
    }

    if (fileInPreview == file) {
      getRenderingQueue().cancelAllUpdates();
      render();
      return;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(file);

    if (facet != null) {
      VirtualFile vFile = file.getVirtualFile();
      vFile = vFile != null ? vFile.getParent() : null;
      vFile = vFile != null ? vFile.getParent() : null;

      if (vFile != null) {
        final VirtualFile finalVFile = vFile;

        myRenderQueue.queue(new Update("saveAndRender") {
          @Override
          public void run() {
            final VirtualFile[] resDirs = facet.getLocalResourceManager().getAllResourceDirs();

            if (ArrayUtil.find(resDirs, finalVFile) >= 0) {
              render();
            }
          }
        });
      }
    }
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        myToolWindowReady = true;
      }
    });
  }

  private void initToolWindow() {
    myToolWindowForm = new AndroidLayoutPreviewToolWindowForm(myProject, this);
    final String toolWindowId = AndroidBundle.message("android.layout.preview.tool.window.title");
    myToolWindow =
      ToolWindowManager.getInstance(myProject).registerToolWindow(toolWindowId, false, ToolWindowAnchor.RIGHT, myProject, true);
    myToolWindow.setIcon(AndroidIcons.AndroidPreview);

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      private boolean myVisible = false;

      @Override
      public void stateChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(toolWindowId);
        if (window != null && window.isAvailable()) {
          final boolean visible = window.isVisible();
          AndroidLayoutPreviewToolWindowSettings.getInstance(myProject).getGlobalState().setVisible(visible);

          if (visible && !myVisible) {
            render();
          }
          myVisible = visible;
        }
      }
    });

    final JPanel contentPanel = myToolWindowForm.getContentPanel();
    final ContentManager contentManager = myToolWindow.getContentManager();
    final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(myToolWindowForm);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  @Override
  public void projectClosed() {
    if (myToolWindowForm != null) {
      Disposer.dispose(myToolWindowForm);
      myToolWindowForm = null;
      myToolWindow = null;
      myToolWindowDisposed = true;
    }
  }

  @Override
  @NotNull
  @NonNls
  public String getComponentName() {
    return "AndroidLayoutPreviewToolWindowManager";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  private void processFileEditorChange(final TextEditor newEditor) {
    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        final Editor activeEditor = newEditor != null ? newEditor.getEditor() : null;

        if (myToolWindow == null) {
          if (activeEditor == null) {
            return;
          }
          initToolWindow();
        }

        final AndroidLayoutPreviewToolWindowSettings settings = AndroidLayoutPreviewToolWindowSettings.getInstance(myProject);
        final boolean hideForNonLayoutFiles = settings.getGlobalState().isHideForNonLayoutFiles();

        if (activeEditor == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(activeEditor.getDocument());
        if (psiFile == null) {
          myToolWindowForm.setFile(null);
          myToolWindow.setAvailable(!hideForNonLayoutFiles, null);
          return;
        }

        final boolean toRender = myToolWindowForm.getFile() != psiFile;
        if (toRender) {
          if (!myToolWindowForm.setFile(psiFile)) {
            return;
          }
        }

        myToolWindow.setAvailable(true, null);
        final boolean visible = AndroidLayoutPreviewToolWindowSettings.getInstance(myProject).getGlobalState().isVisible();
        if (visible) {
          myToolWindow.show(null);
        }

        if (toRender) {
          render();
        }
      }
    });
  }

  void finishSetFile() {
    myToolWindow.setAvailable(true, null);
    final boolean visible = AndroidLayoutPreviewToolWindowSettings.getInstance(myProject).getGlobalState().isVisible();
    if (visible) {
      myToolWindow.show(null);
    }

    render();
  }

  public void render() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    if (myToolWindow == null || !myToolWindow.isVisible()) {
      return;
    }

    final PsiFile psiFile = myToolWindowForm.getFile();
    if (psiFile == null) {
      return;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);
    if (facet == null) {
      return;
    }

    getRenderingQueue().queue(new Update("render") {
      @Override
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          @Override
          public void run() {
            DumbService.getInstance(myProject).waitForSmartMode();
            try {
              doRender(facet, psiFile);
            }
            catch (Throwable e) {
              LOG.error(e);
            }
          }
        }, new AndroidPreviewProgressIndicator(myToolWindowForm, 1000));
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void doRender(@NotNull final AndroidFacet facet, @NotNull final PsiFile psiFile) {
    if (myToolWindowForm == null) {
      return;
    }

    final VirtualFile layoutXmlFile = psiFile.getVirtualFile();
    if (layoutXmlFile == null) {
      return;
    }
    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      Module module = facet.getModule();
      Configuration configuration = myToolWindowForm.getConfiguration();
      if (configuration == null) {
        return;
      }
      final RenderLogger logger = new RenderLogger(layoutXmlFile.getName(), module);
      final RenderService service = RenderService.create(facet, module, psiFile, configuration, logger, myToolWindowForm);
      if (service != null) {
        // Prefetch outside of read lock
        service.getResourceResolver();

        result = ApplicationManager.getApplication().runReadAction(new Computable<RenderResult>() {
          @Nullable
          @Override
          public RenderResult compute() {
            if (psiFile instanceof XmlFile) {
              service.useDesignMode(((XmlFile)psiFile).getRootTag());
            }
            return service.render();
          }
        });
        service.dispose();
      }
      if (result == null) {
        result = new RenderResult(null, null, psiFile, logger);
      }
    }

    if (!getRenderingQueue().isEmpty()) {
      return;
    }

    final RenderResult renderResult = result;
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        final TextEditor editor = getActiveLayoutXmlEditor(); // Must be run from read thread
        myToolWindowForm.setRenderResult(renderResult, editor);
        myToolWindowForm.updatePreviewPanel();

        if (RenderPreviewMode.getCurrent() != RenderPreviewMode.NONE) {
          RenderPreviewManager previewManager = myToolWindowForm.getPreviewPanel().getPreviewManager(myToolWindowForm, true);
          if (previewManager != null) {
            previewManager.renderPreviews();
          }
        }
      }
    });
  }

  @Nullable
  private TextEditor getActiveLayoutXmlEditor() {
    FileEditor[] fileEditors = myFileEditorManager.getSelectedEditors();
    if (fileEditors.length > 0 && fileEditors[0] instanceof TextEditor) {
      final TextEditor textEditor = (TextEditor)fileEditors[0];
      if (isApplicableEditor(textEditor)) {
        return textEditor;
      }
    }
    return null;
  }

  private boolean isApplicableEditor(TextEditor textEditor) {
    final Document document = textEditor.getEditor().getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    return psiFile instanceof XmlFile &&
           AndroidFacet.getInstance(psiFile) != null &&
           LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
  }

  public static AndroidLayoutPreviewToolWindowManager getInstance(Project project) {
    return project.getComponent(AndroidLayoutPreviewToolWindowManager.class);
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      fileChanged(event);
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      fileChanged(event);
    }

    @Override
    public void fileDeleted(VirtualFileEvent event) {
      fileChanged(event);
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      fileChanged(event);
    }

    private void fileChanged(VirtualFileEvent event) {
      if (myToolWindowForm == null || !myToolWindowReady || myToolWindowDisposed) {
        return;
      }

      final PsiFile layoutFile = myToolWindowForm.getFile();
      if (layoutFile == null) {
        return;
      }

      final VirtualFile changedFile = event.getFile();
      final VirtualFile parent = changedFile.getParent();

      if (parent == null) {
        return;
      }

      // TODO: Refresh cached locale folders! (For now, it's computed each time)
      //if (AndroidResourceUtil.isLocalResourceDirectory(parent, myProject)) {
      //  myToolWindowForm.updateLocales();
      //  render();
      //}
      //
      //final VirtualFile gp = parent.getParent();
      //if (gp != null && AndroidResourceUtil.isLocalResourceDirectory(gp, myProject)) {
      //  myToolWindowForm.updateLocales();
      //  render();
      //}
    }
  }

  private class MyAndroidPlatformListener extends ModuleRootAdapter {
    private final Map<Module, Sdk> myModule2Sdk = new HashMap<Module, Sdk>();
    private final Project myProject;

    private MyAndroidPlatformListener(@NotNull Project project) {
      myProject = project;
      updateMap();
    }

    @Override
    public void rootsChanged(ModuleRootEvent event) {
      if (myToolWindowForm == null || !myToolWindowReady || myToolWindowDisposed) {
        return;
      }

      final PsiFile file = myToolWindowForm.getFile();
      if (file != null) {
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        if (module != null) {
          final Sdk prevSdk = myModule2Sdk.get(module);
          final Sdk newSdk = ModuleRootManager.getInstance(module).getSdk();
          if (newSdk != null
              && (newSdk.getSdkType() instanceof AndroidSdkType ||
                  (prevSdk != null && prevSdk.getSdkType() instanceof AndroidSdkType))
              && !newSdk.equals(prevSdk)) {

            final AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)newSdk.getSdkAdditionalData();
            final AndroidPlatform newPlatform = additionalData != null ? additionalData.getAndroidPlatform() : null;
            //myToolWindowForm.updateDevicesAndTargets(newPlatform);
            //myToolWindowForm.updateThemes();

            render();
          }
        }
      }

      updateMap();
    }

    private void updateMap() {
      myModule2Sdk.clear();
      for (Module module : ModuleManager.getInstance(myProject).getModules()) {
        myModule2Sdk.put(module, ModuleRootManager.getInstance(module).getSdk());
      }
    }
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
