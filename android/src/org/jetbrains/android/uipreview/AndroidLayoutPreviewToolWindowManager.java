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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceUrl;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.project.AndroidProjectBuildNotifications;
import com.android.tools.idea.project.AndroidProjectBuildNotifications.AndroidProjectBuildListener;
import com.android.tools.idea.project.AndroidProjectBuildNotifications.BuildContext;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.ProjectTopics;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
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
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Map;

import static com.android.SdkConstants.*;

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
  /**
   * Indicator used to indicate the progress between the time we switch editors to the time the rendering
   * is done
   */
  private AndroidPreviewProgressIndicator myCurrentIndicator;

  private static final Object RENDERING_LOCK = new Object();
  private static final Object PROGRESS_LOCK = new Object();

  public AndroidLayoutPreviewToolWindowManager(final Project project, final FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;

    if (RenderService.NELE_ENABLED) {
      myToolWindowUpdateQueue = null;
      return;
    }

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.layout.preview", 100, true, null, project);

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyAndroidPlatformListener(project));

    initListeners(project);
  }

  protected void initListeners(Project project) {
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
        myIgnoreChildrenChanged = true;
        if (isRelevant(event)) {
          PsiElement child = event.getChild();
          PsiElement parent = event.getParent();

          if (child instanceof XmlAttribute && parent instanceof XmlTag) {
            // Typing in a new attribute. Don't need to do any rendering until there
            // is an actual value
            if (((XmlAttribute)child).getValueElement() == null) {
              return;
            }
          }
          else if (parent instanceof XmlAttribute && child instanceof XmlAttributeValue) {
            XmlAttributeValue attributeValue = (XmlAttributeValue)child;
            if (attributeValue.getValue() == null || attributeValue.getValue().isEmpty()) {
              // Just added a new blank attribute; nothing to render yet
              return;
            }
          }
          else if (parent instanceof XmlAttributeValue && child instanceof XmlToken && event.getOldChild() == null) {
            // Just added attribute value
            String text = child.getText();
            // See if this is an attribute that takes a resource!
            if (text.startsWith(PREFIX_RESOURCE_REF) && !text.startsWith(PREFIX_BINDING_EXPR)) {
              if (text.equals(PREFIX_RESOURCE_REF) || text.equals(ANDROID_PREFIX)) {
                // Using code completion to insert resource reference; not yet done
                return;
              }
              ResourceUrl url = ResourceUrl.parse(text);
              if (url != null && url.name.isEmpty()) {
                // Using code completion to insert resource reference; not yet done
                return;
              }
            }
          }
          update(event);
        }
      }

      @Override
      public void childReplaced(@NotNull PsiTreeChangeEvent event) {
        myIgnoreChildrenChanged = true;
        if (isRelevant(event)) {
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
          else if (parent instanceof XmlAttributeValue && child instanceof XmlToken && event.getOldChild() != null) {
            String newText = child.getText();
            String prevText = event.getOldChild().getText();
            // See if user is working on an incomplete URL, and is still not complete, e.g. typing in @string/foo manually
            if (newText.startsWith(PREFIX_RESOURCE_REF) && !newText.startsWith(PREFIX_BINDING_EXPR)) {
              ResourceUrl prevUrl = ResourceUrl.parse(prevText);
              ResourceUrl newUrl = ResourceUrl.parse(newText);
              if (prevUrl != null && prevUrl.name.isEmpty()) {
                prevUrl = null;
              }
              if (newUrl != null && newUrl.name.isEmpty()) {
                newUrl = null;
              }
              if (prevUrl == null && newUrl == null) {
                return;
              }
            }
          }
          update(event);
        }
      }

      @Override
      public void childRemoved(@NotNull PsiTreeChangeEvent event) {
        myIgnoreChildrenChanged = true;
        if (isRelevant(event)) {
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

    AndroidProjectBuildNotifications.subscribe(project, new AndroidProjectBuildListener() {
      @Override
      public void buildComplete(@NotNull BuildContext context) {
        if (myToolWindowForm != null && myToolWindowReady && !myToolWindowDisposed) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              render();
            }
          });
        }
      }
    });
  }

  @NotNull
  private MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue =
          new MergingUpdateQueue("android.layout.rendering", 800, true, null, myProject, null, Alarm.ThreadToUse.OWN_THREAD);
      }
      return myRenderingQueue;
    }
  }

  @VisibleForTesting
  public boolean isRenderPending() {
    MergingUpdateQueue queue = getRenderingQueue();
    return !queue.isEmpty() && !queue.isFlushing() && !myToolWindowUpdateQueue.isEmpty() && !myToolWindowUpdateQueue.isFlushing();
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
    if (child instanceof PsiWhiteSpace || child instanceof PsiErrorElement || child instanceof XmlComment || parent instanceof XmlComment) {
      return false;
    }

    return true;
  }

  protected void update(PsiTreeChangeEvent event) {
    if (isRelevant(event)) {
      getRenderingQueue().cancelAllUpdates();
      render();
    }
  }

  @Override
  public void projectOpened() {
    if (RenderService.NELE_ENABLED) {
      return;
    }

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

  protected boolean isRenderAutomatically() {
    return true;
  }

  protected boolean isForceHideOnStart() {
    return false;
  }

  protected ToolWindow getToolWindow() {
    return myToolWindow;
  }

  @Nullable
  protected AnAction getCustomRefreshRenderAction() {
    return null;
  }

  protected void initToolWindow() {
    myToolWindowForm = new AndroidLayoutPreviewToolWindowForm(this, getCustomRefreshRenderAction());
    final String toolWindowId = getToolWindowId();
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
          AndroidEditorSettings.getInstance().getGlobalState().setVisible(visible);

          if (visible && !myVisible && isRenderAutomatically()) {
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
    myToolWindowForm.setUseInteractiveSelector(isUseInteractiveSelector());
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
        final boolean visible = AndroidEditorSettings.getInstance().getGlobalState().isVisible();
        if (visible) {
          // Clear out the render result for the previous file, such that it doesn't briefly show between the time the
          // tool window is shown and the time the render has completed
          if (!myToolWindow.isVisible()) {
            RenderResult renderResult = myToolWindowForm.getRenderResult();
            if (renderResult != null && renderResult.getFile() != psiFile) {
              myToolWindowForm.setRenderResult(RenderResult.createBlank(psiFile, null), null);
            }
          }
          myToolWindow.show(null);
        }

        if (toRender) {
          boolean requestedRender = render();
          if (requestedRender) {
            if (renderImmediately) {
              getRenderingQueue().sendFlush();
            }
            AndroidLayoutPreviewToolWindowForm toolWindowForm = myToolWindowForm;
            synchronized (PROGRESS_LOCK) {
              if (myCurrentIndicator == null) {
                myCurrentIndicator = new AndroidPreviewProgressIndicator(toolWindowForm, 0);
                myCurrentIndicator.start();
              }
            }
          }
        }
      }
    });
  }

  public static void renderIfApplicable(@Nullable final Project project) {
    if (project != null) {
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            renderIfApplicable(project);
          }
        });
        return;
      }
      AndroidLayoutPreviewToolWindowManager preview = getInstance(project);
      if (preview != null) {
        preview.render();
      }
    }
  }

  public boolean render() {
    if (myToolWindow == null || !myToolWindow.isVisible()) {
      return false;
    }

    final PsiFile psiFile = myToolWindowForm.getFile();
    if (psiFile == null) {
      return false;
    }

    final AndroidFacet facet = AndroidFacet.getInstance(psiFile);
    if (facet == null) {
      return false;
    }

    return render(psiFile, facet, false);
  }

  protected boolean render(final PsiFile psiFile, final AndroidFacet facet,
                        @SuppressWarnings("unused") boolean forceFullRender) {
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
            synchronized (PROGRESS_LOCK) {
              if (myCurrentIndicator != null) {
                myCurrentIndicator.stop();
                myCurrentIndicator = null;
              }
            }
          }
        }, new AndroidPreviewProgressIndicator(myToolWindowForm, 100));
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
    return true;
  }

  public void flush() {
    getRenderingQueue().sendFlush();
  }

  private void doRender(@NotNull final AndroidFacet facet, @NotNull final PsiFile psiFile) {
    if (myProject.isDisposed()) {
      return;
    }

    final AndroidLayoutPreviewToolWindowForm toolWindowForm = myToolWindowForm;
    if (toolWindowForm == null) {
      return;
    }

    final VirtualFile layoutXmlFile = psiFile.getVirtualFile();
    String loggerName = (layoutXmlFile!=null) ? layoutXmlFile.getName() : psiFile.getName();

    Module module = facet.getModule();
    Configuration configuration = toolWindowForm.getConfiguration();
    if (configuration == null) {
      return;
    }

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParserFactory.saveFileIfNecessary(psiFile);

    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      RenderService renderService = RenderService.get(facet);
      RenderLogger logger = renderService.createLogger();
      final RenderTask task = renderService.createTask(psiFile, configuration, logger, toolWindowForm);
      if (task != null) {
        task.useDesignMode(psiFile);
        result = task.render();
        task.dispose();
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
    // selectedEditor will be the current active text editor. If we are in a split view, that means the one that currently has the caret.
    Editor selectedEditor = myFileEditorManager.getSelectedTextEditor();
    if (selectedEditor != null) {
      // FileEditorManager.getSelectedEditors will return all the editors that are currently visible. If we have a split view, it will return
      // all the editors in the split.
      // We use selectedEditor to find the one that currently has the caret.
      for (FileEditor fileEditor : myFileEditorManager.getSelectedEditors()) {
        if (fileEditor instanceof TextEditor) {
          TextEditor textEditor = (TextEditor)fileEditor;
          if (isApplicableEditor(textEditor) && (textEditor.getEditor() == selectedEditor)) {
            return (TextEditor)fileEditor;
          }
        }
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

  private static boolean isInResourceFolder(@Nullable PsiFile psiFile) {
    if (psiFile instanceof XmlFile && AndroidFacet.getInstance(psiFile) != null) {
      return RenderService.canRender(psiFile);
    }
    return false;
  }

  public static AndroidLayoutPreviewToolWindowManager getInstance(Project project) {
    return project.getComponent(AndroidLayoutPreviewToolWindowManager.class);
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
    if (renderImmediately) {
      myRenderImmediately = true;
    }
    processFileEditorChange(editor);
    if (renderImmediately) {
      myToolWindowUpdateQueue.sendFlush();
    }
  }

  @VisibleForTesting
  public AndroidLayoutPreviewToolWindowForm getToolWindowForm() {
    return myToolWindowForm;
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
          if (newSdk != null &&
              (newSdk.getSdkType() instanceof AndroidSdkType || (prevSdk != null && prevSdk.getSdkType() instanceof AndroidSdkType)) &&
              !newSdk.equals(prevSdk)) {
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
