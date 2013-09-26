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

import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.ProjectTopics;
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
import com.intellij.openapi.module.ModuleUtilCore;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Alarm;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
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
  @SuppressWarnings("SpellCheckingInspection")
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager");

  private final MergingUpdateQueue myToolWindowUpdateQueue;

  private final Object myRenderingQueueLock = new Object();
  private MergingUpdateQueue myRenderingQueue;

  private final Project myProject;
  private final FileEditorManager myFileEditorManager;

  private AndroidLayoutPreviewToolWindowForm myToolWindowForm;
  private ToolWindow myToolWindow;
  private boolean myToolWindowReady = false;
  private boolean myToolWindowDisposed = false;

  private static final Object RENDERING_LOCK = new Object();

  public AndroidLayoutPreviewToolWindowManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.preview", 300, true, null, project);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener(project));

    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
      boolean myIgnoreChildrenChanged;

      @Override
      public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
        myIgnoreChildrenChanged = false;
      }

      @Override
      public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
        // See ResourceFolderManager#PsiListener#childrenChanged
        if (isRelevant(event) && !myIgnoreChildrenChanged && event.getParent() != event.getChild()) {
          update(event);
        }
      }

      @Override
      public void childAdded(@NotNull PsiTreeChangeEvent event) {
        if (isRelevant(event)) {
          myIgnoreChildrenChanged = true;
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (child instanceof XmlAttribute && parent instanceof XmlTag) {
            // Typing in a new attribute. Don't need to do any rendering until there
            // is an actual value
            if (((XmlAttribute)child).getValueElement() == null) {
              return;
            }
          } else if (parent instanceof XmlAttribute && child instanceof XmlAttributeValue) {
            XmlAttributeValue attributeValue = (XmlAttributeValue)child;
            if (attributeValue.getValue() == null || attributeValue.getValue().isEmpty()) {
              // Just added a new blank attribute; nothing to render yet
              return;
            }
          }
          update(event);
        }
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        if (isRelevant(event)) {
          myIgnoreChildrenChanged = true;
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (parent instanceof XmlAttribute && child instanceof XmlToken) {
            // Typing in attribute name. Don't need to do any rendering until there
            // is an actual value
            XmlAttributeValue valueElement = ((XmlAttribute)parent).getValueElement();
            if (valueElement == null || valueElement.getValue() == null || valueElement.getValue().isEmpty()) {
              return;
            }
          }
          update(event);
        }
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        if (isRelevant(event)) {
          myIgnoreChildrenChanged = true;
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (parent instanceof XmlAttribute && child instanceof XmlToken) {
            // Typing in attribute name. Don't need to do any rendering until there
            // is an actual value
            XmlAttributeValue valueElement = ((XmlAttribute)parent).getValueElement();
            if (valueElement == null || valueElement.getValue() == null || valueElement.getValue().isEmpty()) {
              return;
            }
          }
          update(event);
        }
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

  private boolean isRelevant(PsiTreeChangeEvent event) {
    if (myToolWindowForm == null || !myToolWindowReady || myToolWindowDisposed) {
      return false;
    }
    final PsiFile fileInPreview = myToolWindowForm.getFile();
    final PsiFile file = event.getFile();

    if (fileInPreview == null || file == null || fileInPreview != file) {
      return false;
    }

    PsiElement child = event.getChild();
    PsiElement parent = event.getParent();

    // We can ignore edits in whitespace, and in XML error nodes, and in comments
    // (Note that editing text in an attribute value, including whitespace characters,
    // is not a PsiWhiteSpace element; it's an XmlToken of token type XML_ATTRIBUTE_VALUE_TOKEN
    if (child instanceof PsiWhiteSpace || child instanceof PsiErrorElement
        || child instanceof XmlComment || parent instanceof XmlComment) {
      return false;
    }

    return true;
  }

  private void update(PsiTreeChangeEvent event) {
    if (isRelevant(event)) {
      getRenderingQueue().cancelAllUpdates();
      render();
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
    @SuppressWarnings("ConstantConditions")
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

  private void processFileEditorChange(@Nullable final TextEditor newEditor) {
    myToolWindowUpdateQueue.cancelAllUpdates();
    myToolWindowUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (!myToolWindowReady || myToolWindowDisposed) {
          return;
        }
        final Editor activeEditor = newEditor != null ? newEditor.getEditor() : null;

        if (myToolWindow == null) {
          if (activeEditor == null || !activeEditor.getComponent().isShowing()) {
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
          // Clear out the render result for the previous file, such that it doesn't briefly show between the time the
          // tool window is shown and the time the render has completed
          if (!myToolWindow.isVisible()) {
            RenderResult renderResult = myToolWindowForm.getRenderResult();
            if (renderResult != null && renderResult.getFile() != null && renderResult.getFile() != psiFile) {
              myToolWindowForm.setRenderResult(RenderResult.createBlank(psiFile, null), null);
            }
          }
          myToolWindow.show(null);
        }

        if (toRender) {
          render();
        }
      }
    });
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
    final AndroidLayoutPreviewToolWindowForm toolWindowForm = myToolWindowForm;
    if (toolWindowForm == null) {
      return;
    }

    final VirtualFile layoutXmlFile = psiFile.getVirtualFile();
    if (layoutXmlFile == null) {
      return;
    }
    Module module = facet.getModule();
    Configuration configuration = toolWindowForm.getConfiguration();
    if (configuration == null) {
      return;
    }
    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      final RenderLogger logger = new RenderLogger(layoutXmlFile.getName(), module);
      final RenderService service = RenderService.create(facet, module, psiFile, configuration, logger, toolWindowForm);
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
        result = RenderResult.createBlank(psiFile, logger);
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
    // In theory, we should just check
    //   LayoutDomFileDescription.isLayoutFile((XmlFile)psiFile);
    // here, but there are problems where files don't show up with layout preview
    // at startup, presumably because the resource directories haven't been properly
    // initialized yet.
    return isInResourceFolder(psiFile, ResourceType.LAYOUT);
  }

  private static boolean isInResourceFolder(@Nullable PsiFile psiFile, @NotNull ResourceType type) {
    if (psiFile instanceof XmlFile && AndroidFacet.getInstance(psiFile) != null) {
      PsiDirectory parent = psiFile.getParent();
      if (parent != null) {
        String parentName = parent.getName();
        String typeName = type.getName();
        if (parentName.startsWith(typeName) &&
            (typeName.equals(parentName) || parentName.charAt(typeName.length()) == '-')) {
          return true;
        }
      }
    }
    return false;
  }

  public static AndroidLayoutPreviewToolWindowManager getInstance(Project project) {
    return project.getComponent(AndroidLayoutPreviewToolWindowManager.class);
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
        final Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module != null) {
          final Sdk prevSdk = myModule2Sdk.get(module);
          final Sdk newSdk = ModuleRootManager.getInstance(module).getSdk();
          if (newSdk != null
              && (newSdk.getSdkType() instanceof AndroidSdkType ||
                  (prevSdk != null && prevSdk.getSdkType() instanceof AndroidSdkType))
              && !newSdk.equals(prevSdk)) {
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
