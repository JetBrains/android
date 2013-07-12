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
package com.intellij.android.designer.designSurface;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.resources.Density;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.ConfigurationToolBar;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.google.common.primitives.Ints;
import com.intellij.android.designer.componentTree.AndroidTreeDecorator;
import com.intellij.android.designer.inspection.ErrorAnalyzer;
import com.intellij.android.designer.model.*;
import com.intellij.android.designer.model.layout.actions.ToggleRenderModeAction;
import com.intellij.designer.DesignerEditor;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.actions.DesignerActionPanel;
import com.intellij.designer.componentTree.TreeComponentDecorator;
import com.intellij.designer.designSurface.*;
import com.intellij.designer.designSurface.tools.ComponentCreationFactory;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;
import com.intellij.designer.model.WrapInProvider;
import com.intellij.designer.palette.DefaultPaletteItem;
import com.intellij.designer.palette.PaletteGroup;
import com.intellij.designer.palette.PaletteItem;
import com.intellij.designer.palette.PaletteToolWindowManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Alarm;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.AndroidExtractAsIncludeAction;
import org.jetbrains.android.refactoring.AndroidExtractStyleAction;
import org.jetbrains.android.refactoring.AndroidInlineIncludeAction;
import org.jetbrains.android.refactoring.AndroidInlineStyleReferenceAction;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewPanel;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.android.tools.idea.configurations.ConfigurationListener.MASK_ALL;
import static com.android.tools.idea.configurations.ConfigurationListener.MASK_RENDERING;
import static com.android.tools.idea.gradle.variant.view.BuildVariantView.BuildVariantSelectionChangeListener;
import static com.android.tools.idea.rendering.RenderErrorPanel.SIZE_ERROR_PANEL_DYNAMICALLY;
import static com.intellij.designer.designSurface.ZoomType.FIT_INTO;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditorPanel extends DesignerEditorPanel implements RenderContext, BuildVariantSelectionChangeListener {
  private static final int DEFAULT_HORIZONTAL_MARGIN = 30;
  private static final int DEFAULT_VERTICAL_MARGIN = 20;
  private static final Integer LAYER_ERRORS = LAYER_INPLACE_EDITING + 150; // Must be an Integer, not an int; see JLayeredPane.addImpl
  private static final Integer LAYER_PREVIEW = LAYER_INPLACE_EDITING + 170; // Must be an Integer, not an int; see JLayeredPane.addImpl

  private final TreeComponentDecorator myTreeDecorator = new AndroidTreeDecorator();
  private final XmlFile myXmlFile;
  private final ExternalPSIChangeListener myPsiChangeListener;
  private final Alarm mySessionAlarm = new Alarm();
  private final MergingUpdateQueue mySessionQueue;
  private final AndroidDesignerEditorPanel.LayoutConfigurationListener myConfigListener;
  private final AndroidFacet myFacet;
  private volatile RenderSession mySession;
  private volatile long mySessionId;
  private final Lock myRendererLock = new ReentrantLock();
  private WrapInProvider myWrapInProvider;
  private RootView myRootView;
  private boolean myShowingRoot;
  private RenderPreviewTool myPreviewTool;

  @Nullable private Configuration myConfiguration;
  private int myConfigurationDirty;
  private boolean myActive;
  private boolean myVariantChanged;

  /** Zoom level (1 = 100%). TODO: Persist this setting across IDE sessions (on a per file basis) */
  private double myZoom = 1;
  private ZoomType myZoomMode = ZoomType.FIT_INTO;
  private RenderPreviewManager myPreviewManager;

  public AndroidDesignerEditorPanel(@NotNull DesignerEditor editor,
                                    @NotNull Project project,
                                    @NotNull Module module,
                                    @NotNull VirtualFile file) {
    super(editor, project, module, file);

    showProgress("Loading configuration...");

    AndroidFacet facet = AndroidFacet.getInstance(getModule());
    assert facet != null;
    myFacet = facet;
    // The configuration depends on project state, which may not yet be available: defer
    boolean initializeConfiguration = true;
    if (facet.isGradleProject()) {
      IdeaAndroidProject gradleProject = facet.getIdeaAndroidProject();
      if (gradleProject == null) {
        initializeConfiguration = false;
        // Still syncing model; typically on IDE restart when the editor is reopened but
        // project model has not yet been fully initialized
        facet.addListener(new AndroidFacet.GradleProjectAvailableListener() {
          @Override
          public void gradleProjectAvailable(@NotNull IdeaAndroidProject project) {
            myFacet.removeListener(this);
            initializeConfiguration();
            requestRender();
          }
        });
      }
      BuildVariantView variantView = BuildVariantView.getInstance(facet.getModule().getProject());
      if (variantView != null) {
        // Ensure that the project resources have been initialized first, since
        // we want it to add its own variant listeners before ours (such that
        // when the variant changes, the project resources get notified and updated
        // before our own update listener attempts a re-render)
        facet.getProjectResources(false /*libraries*/, true /*createIfNecessary*/);

        variantView.removeListener(this);
        variantView.addListener(this);
      }
    }
    myConfigListener = new LayoutConfigurationListener();
    if (initializeConfiguration) {
      initializeConfiguration();
    }

    mySessionQueue = ViewsMetaManager.getInstance(project).getSessionQueue();
    myXmlFile = (XmlFile)ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      @Nullable
      public PsiFile compute() {
        return PsiManager.getInstance(getProject()).findFile(myFile);
      }
    });
    myPsiChangeListener = new ExternalPSIChangeListener(this, myXmlFile, 100, new Runnable() {
      @Override
      public void run() {
        reparseFile();
      }
    });

    addActions();

    myActive = true;
    myPsiChangeListener.setInitialize();
    myPsiChangeListener.activate();
    myPsiChangeListener.addRequest();
  }

  private void initializeConfiguration() {
    myConfiguration = myFacet.getConfigurationManager().getConfiguration(myFile);
    myConfiguration.addListener(myConfigListener);
  }

  private void addActions() {
    addConfigurationActions();
    myActionPanel.getPopupGroup().addSeparator();
    myActionPanel.getPopupGroup().add(buildRefactorActionGroup());
    addGotoDeclarationAction();
  }

  private void addGotoDeclarationAction() {
    AnAction gotoDeclaration = new AnAction("Go To Declaration") {
      @Override
      public void update(AnActionEvent e) {
        EditableArea area = e.getData(EditableArea.DATA_KEY);
        e.getPresentation().setEnabled(area != null && area.getSelection().size() == 1);
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        EditableArea area = e.getData(EditableArea.DATA_KEY);
        if (area != null) {
          RadViewComponent component = (RadViewComponent)area.getSelection().get(0);
          PsiNavigateUtil.navigate(component.getTag());
        }
      }
    };
    myActionPanel.registerAction(gotoDeclaration, IdeActions.ACTION_GOTO_DECLARATION);
    myActionPanel.getPopupGroup().add(gotoDeclaration);
  }

  private void addConfigurationActions() {
    DefaultActionGroup designerActionGroup = getActionPanel().getActionGroup();
    ActionGroup group = ConfigurationToolBar.createActions(this);
    designerActionGroup.add(group);
  }

  @Override
  protected DesignerActionPanel createActionPanel() {
    return new AndroidDesignerActionPanel(this, myGlassLayer);
  }

  @Override
  protected CaptionPanel createCaptionPanel(boolean horizontal) {
    // No borders; not necessary since we have a different designer background than the caption area
    return new CaptionPanel(this, horizontal, false);
  }

  @Override
  protected JScrollPane createScrollPane(@NotNull JLayeredPane content) {
    // No background color
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(content);
    scrollPane.setBackground(null);
    return scrollPane;
  }

  @NotNull
  private static ActionGroup buildRefactorActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup("_Refactor", true);
    final ActionManager manager = ActionManager.getInstance();

    AnAction action = manager.getAction(AndroidExtractStyleAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Extract Style...", action));

    action = manager.getAction(AndroidInlineStyleReferenceAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("_Inline Style...", action));

    action = manager.getAction(AndroidExtractAsIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("E_xtract Layout...", action));

    action = manager.getAction(AndroidInlineIncludeAction.ACTION_ID);
    group.add(new AndroidRefactoringActionWrapper("I_nline Layout...", action));
    return group;
  }

  private void reparseFile() {
    try {
      storeState();
      showDesignerCard();

      parseFile(new Runnable() {
        @Override
        public void run() {
          showDesignerCard();
          myLayeredPane.revalidate();
          restoreState();
        }
      });
    }
    catch (RuntimeException e) {
      myPsiChangeListener.clear();
      showError("Parsing error", e.getCause() == null ? e : e.getCause());
    }
  }

  private void parseFile(final Runnable runnable) {
    final ModelParser parser = new ModelParser(getProject(), myXmlFile);
    if (myConfiguration == null) {
      return;
    }

    createRenderer(new MyThrowable(), new ThrowableConsumer<RenderResult, Throwable>() {
      @Override
      public void consume(RenderResult result) throws Throwable {
        RenderSession session = result.getSession();
        if (session == null) {
          return;
        }

        if (!session.getResult().isSuccess()) {
          // This image may not have been fully rendered before some error caused
          // the render to abort, but a partial render is better. However, if the render
          // was due to some configuration change, we don't want to replace the image
          // since all the mouse regions and model setup will no longer match the pixels.
          if (myRootView != null && myRootView.getImage() != null && session.getImage() != null &&
            session.getImage().getWidth() == myRootView.getImage().getWidth() &&
            session.getImage().getHeight() == myRootView.getImage().getHeight()) {
            myRootView.setImage(session.getImage(), session.isAlphaChannelImage());
            myRootView.repaint();
          }
          return;
        }

        boolean insertPanel = !myShowingRoot;
        if (myRootView == null) {
          myRootView = new RootView(AndroidDesignerEditorPanel.this, 0, 0, session.getImage(), session.isAlphaChannelImage());
          myRootView.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent componentEvent) {
              zoomToFitIfNecessary();
            }
          });
          insertPanel = true;
        }
        else {
          myRootView.setImage(session.getImage(), session.isAlphaChannelImage());
          myRootView.updateBounds(true);
        }
        try {
          parser.updateRootComponent(myConfiguration.getFullConfig(), session, myRootView);
        }
        catch (Throwable e) {
          myRootComponent = parser.getRootComponent();
          throw e;
        }
        RadViewComponent newRootComponent = parser.getRootComponent();

        newRootComponent.setClientProperty(ModelParser.XML_FILE_KEY, myXmlFile);
        newRootComponent.setClientProperty(ModelParser.MODULE_KEY, AndroidDesignerEditorPanel.this);
        newRootComponent.setClientProperty(TreeComponentDecorator.KEY, myTreeDecorator);

        IAndroidTarget target = myConfiguration.getTarget();
        assert target != null; // otherwise, rendering would not have succeeded
        PropertyParser propertyParser = new PropertyParser(getModule(), target);
        newRootComponent.setClientProperty(PropertyParser.KEY, propertyParser);
        propertyParser.loadRecursive(newRootComponent);

        boolean firstRender = myRootComponent == null;

        myRootComponent = newRootComponent;

        // Start out selecting the root layout rather than the device item; this will
        // show relevant layout actions immediately, will cause the component tree to
        // be properly expanded, etc
        if (firstRender) {
          RadViewComponent rootComponent = getLayoutRoot();
          if (rootComponent != null) {
            mySurfaceArea.setSelection(Collections.<RadComponent>singletonList(rootComponent));
          }
        }

        if (insertPanel) {
          // Use a custom layout manager which adjusts the margins/padding around the designer canvas
          // dynamically; it will try to use DEFAULT_HORIZONTAL_MARGIN * DEFAULT_VERTICAL_MARGIN, but
          // if there is not enough room, it will split the margins evenly in each dimension until
          // there is no room available without scrollbars.
          JPanel rootPanel = new JPanel(new LayoutManager() {
            @Override
            public void addLayoutComponent(String s, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container container) {
              return new Dimension(0, 0);
            }

            @Override
            public Dimension minimumLayoutSize(Container container) {
              return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container container) {
              myRootView.updateBounds(false);
              int x = Math.max(2, Math.min(DEFAULT_HORIZONTAL_MARGIN, (container.getWidth() - myRootView.getWidth()) / 2));
              int y = Math.max(2, Math.min(DEFAULT_VERTICAL_MARGIN, (container.getHeight() - myRootView.getHeight()) / 2));

              // If we're squeezing the image to fit, and there's a drop shadow showing
              // shift *some* space away from the tail portion of the drop shadow over to
              // the left to make the image look more balanced
              if (myRootView.getShowDropShadow()) {
                if (x <= 2) {
                  x += ShadowPainter.SHADOW_SIZE / 3;
                }
                if (y <= 2) {
                  y += ShadowPainter.SHADOW_SIZE / 3;
                }
              }

              if (myMaxWidth > 0) {
                myRootView.setLocation(Math.max(0, (myMaxWidth - myRootView.getScaledWidth()) / 2),
                                         2 + Math.max(0, (myMaxHeight - myRootView.getScaledHeight()) / 2));
              }
            }
          });

          rootPanel.setBackground(AndroidLayoutPreviewPanel.DESIGNER_BACKGROUND_COLOR);
          rootPanel.setOpaque(true);
          rootPanel.add(myRootView);
          myLayeredPane.add(rootPanel, LAYER_COMPONENT);
          myShowingRoot = true;
        }
        zoomToFitIfNecessary();

        loadInspections(new EmptyProgressIndicator());
        updateInspections();

        if (RenderPreviewMode.getCurrent() != RenderPreviewMode.NONE) {
          RenderPreviewManager previewManager = getPreviewManager(true);
          if (previewManager != null) {
            previewManager.renderPreviews();
          }
        }

        runnable.run();
      }
    });
  }

  private void createRenderer(final MyThrowable throwable,
                              final ThrowableConsumer<RenderResult, Throwable> runnable) {
    disposeRenderer();
    if (myConfiguration == null) {
      return;
    }

    mySessionAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (mySession == null) {
          showProgress("Initializing Rendering Library...");
        }
      }
    }, 500);

    final long sessionId = ++mySessionId;

    mySessionQueue.queue(new Update("render") {
      private void cancel() {
        mySessionAlarm.cancelAllRequests();
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!isProjectClosed()) {
              hideProgress();
            }
          }
        });
      }

      @Override
      public void run() {
        try {
          if (sessionId != mySessionId) {
            cancel();
            return;
          }

          final Module module = getModule();
          final RenderLogger logger = new RenderLogger(myFile.getName(), module);

          AndroidFacet facet = AndroidFacet.getInstance(module);
          if (facet == null) {
            logger.error(null, "No Android facet available", null);
          } else if (myConfiguration.getTarget() == null) {
            logger.error(null, "No render target selected", null);
          } else if (myConfiguration.getTheme() == null) {
            logger.error(null, "No theme selected", null);
          }

          if (logger.hasProblems()) {
            cancel();
            RenderResult renderResult = new RenderResult(null, null, myXmlFile, logger);
            runnable.consume(renderResult);
            updateErrors(renderResult);
            return;
          }

          final RenderResult renderResult;
          RenderContext renderContext = AndroidDesignerEditorPanel.this;
          if (myRendererLock.tryLock()) {
            try {
              final RenderService service = RenderService.create(facet, module, myXmlFile, myConfiguration, logger, renderContext);
              if (service != null) {
                // Prefetch outside of read lock
                service.getResourceResolver();
                renderResult = ApplicationManager.getApplication().runReadAction(new Computable<RenderResult>() {
                  @Nullable
                  @Override
                  public RenderResult compute() {
                    if (!ToggleRenderModeAction.isRenderViewPort()) {
                      service.useDesignMode(myXmlFile.getRootTag());
                    }
                    return service.render();
                  }
                });
                service.dispose();
              } else {
                renderResult = new RenderResult(null, null, myXmlFile, logger);
              }
            }
            finally {
              myRendererLock.unlock();
            }
          }
          else {
            cancel();
            return;
          }

          if (sessionId != mySessionId) {
            cancel();
            return;
          }

          if (renderResult == null) {
            throw new RenderingException();
          }

          mySessionAlarm.cancelAllRequests();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              try {
                if (!isProjectClosed()) {
                  hideProgress();
                  if (sessionId == mySessionId) {
                    runnable.consume(renderResult);
                    updateErrors(renderResult);
                  }
                }
              }
              catch (Throwable e) {
                myPsiChangeListener.clear();
                showError("Parsing error", throwable.wrap(e));
              }
            }
          });
        }
        catch (final Throwable e) {
          myPsiChangeListener.clear();
          mySessionAlarm.cancelAllRequests();

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myPsiChangeListener.clear();
              showError("Render error", throwable.wrap(e));
            }
          });
        }
      }
    });
  }

  public int getDpi() {
    return myConfiguration != null ? myConfiguration.getDensity().getDpiValue() : Density.DEFAULT_DENSITY;
  }

  private MyRenderPanelWrapper myErrorPanelWrapper;

  private void updateErrors(@NotNull RenderResult result) {
    RenderLogger logger = result.getLogger();
    if (!logger.hasProblems()) {
      if (myErrorPanelWrapper == null) {
        return;
      }
      myLayeredPane.remove(myErrorPanelWrapper);
      myErrorPanelWrapper = null;
      myLayeredPane.repaint();
    } else {
      if (myErrorPanelWrapper == null) {
        myErrorPanelWrapper = new MyRenderPanelWrapper(new RenderErrorPanel());
      }
      myErrorPanelWrapper.getErrorPanel().showErrors(result);
      myLayeredPane.add(myErrorPanelWrapper, LAYER_ERRORS);
      myLayeredPane.repaint();
    }
  }

  private void disposeRenderer() {
    if (mySession != null) {
      mySession.dispose();
      mySession = null;
    }
  }

  private void updateRenderer(final boolean updateProperties) {
    if (myConfiguration == null) {
      return;
    }
    if (myRootComponent == null) {
      reparseFile();
      return;
    }
    createRenderer(new MyThrowable(), new ThrowableConsumer<RenderResult, Throwable>() {
      @Override
      public void consume(RenderResult result) throws Throwable {
        RenderSession session = result.getSession();
        if (session == null || session.getImage() == null) {
          return;
        }
        RadViewComponent rootComponent = (RadViewComponent)myRootComponent;
        RootView rootView = (RootView)rootComponent.getNativeComponent();
        rootView.setImage(session.getImage(), session.isAlphaChannelImage());
        ModelParser.updateRootComponent(myConfiguration.getFullConfig(), rootComponent, session, rootView);

        zoomToFitIfNecessary();

        myLayeredPane.revalidate();
        myHorizontalCaption.update();
        myVerticalCaption.update();

        DesignerToolWindowManager.getInstance(AndroidDesignerEditorPanel.this).refresh(updateProperties);

        if (RenderPreviewMode.getCurrent() != RenderPreviewMode.NONE) {
          RenderPreviewManager previewManager = getPreviewManager(true);
          if (previewManager != null) {
            previewManager.renderPreviews();
          }
        }
      }
    });
  }

  /**
   * Auto fits the scene, if requested. This will be the case the first time
   * the layout is opened, and after orientation or device changes.
   */
  synchronized void zoomToFitIfNecessary() {
    if (isZoomToFit()) {
      if (myZoomInProgress) {
        // Prevent nested zooming when (e.g. oscillating between two values)
        return;
      }
      try {
        myZoomInProgress = true;
        updateZoom();
      } finally {
        myZoomInProgress = false;
      }
    }
  }

  private boolean myZoomInProgress;

  private void removeNativeRoot() {
    if (myRootComponent != null) {
      Component component = ((RadVisualComponent)myRootComponent).getNativeComponent();
      if (component != null) {
        myLayeredPane.remove(component.getParent());
        myShowingRoot = false;
      }
    }
  }

  @Override
  protected void configureError(@NotNull ErrorInfo info) {
    // Error messages for the user (broken custom views, missing resources, etc) are already
    // trapped during rendering and shown in the error panel. These errors are internal errors
    // in the layout editor and should instead be redirected to the log.
    info.myShowMessage = false;
    info.myShowLog = true;

    Throwable renderCreator = null;
    if (info.myThrowable instanceof MyThrowable) {
      renderCreator = info.myThrowable;
      info.myThrowable = ((MyThrowable)info.myThrowable).original;
    }

    StringBuilder builder = new StringBuilder();

    builder.append("ActiveTool: ").append(myToolProvider.getActiveTool());
    builder.append("\nSDK: ");

    try {
      AndroidPlatform platform = AndroidPlatform.getInstance(getModule());
      if (platform != null) {
        IAndroidTarget target = platform.getTarget();
        builder.append(target.getFullName()).append(" - ").append(target.getVersion());
      }
    }
    catch (Throwable e) {
      builder.append("<unknown>");
    }

    if (renderCreator != null) {
      builder.append("\nCreateRendererStack:\n");
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(stream);
      try {
        renderCreator.printStackTrace(printStream);
        builder.append(stream.toString());
      } finally {
        printStream.close();
      }
    }

    if (info.myThrowable instanceof IndexOutOfBoundsException && myRootComponent != null && mySession != null) {
      builder.append("\n-------- RadTree --------\n");
      ModelParser.printTree(builder, myRootComponent, 0);
      builder.append("\n-------- ViewTree(").append(mySession.getRootViews().size()).append(") --------\n");
      for (ViewInfo viewInfo : mySession.getRootViews()) {
        ModelParser.printTree(builder, viewInfo, 0);
      }
    }

    info.myMessage = builder.toString();
  }

  @Override
  protected void showErrorPage(ErrorInfo info) {
    myPsiChangeListener.clear();
    mySessionAlarm.cancelAllRequests();
    removeNativeRoot();
    super.showErrorPage(info);
  }

  @Override
  public void activate() {
    myActive = true;
    myPsiChangeListener.activate();

    if (myVariantChanged || myPsiChangeListener.isUpdateRenderer() || ((myConfigurationDirty & MASK_RENDERING) != 0)) {
      myVariantChanged = false;
      updateRenderer(true);
    } else if (myRootComponent != null && myRootView != null) {
      if (RenderPreviewMode.getCurrent() != RenderPreviewMode.NONE) {
        RenderPreviewManager previewManager = getPreviewManager(true);
        if (previewManager != null) {
          previewManager.renderPreviews();
        }
      }
    }
    myConfigurationDirty = 0;
  }

  @Override
  public void deactivate() {
    myActive = false;
    myPsiChangeListener.deactivate();
  }

  public void buildProject() {
    if (myPsiChangeListener.ensureUpdateRenderer() && myRootComponent != null) {
      updateRenderer(true);
    }
  }

  @Override
  public void dispose() {
    myPsiChangeListener.dispose();
    if (myConfiguration != null) {
      myConfiguration.removeListener(myConfigListener);
    }
    super.dispose();

    disposeRenderer();

    if (myPreviewManager != null) {
      myPreviewManager.dispose();
      myPreviewManager = null;
    }
  }

  @Override
  @Nullable
  protected Module findModule(Project project, VirtualFile file) {
    Module module = super.findModule(project, file);
    if (module == null) {
      module = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
        @Nullable
        @Override
        public Module compute() {
          return ModuleUtilCore.findModuleForPsiElement(myXmlFile);
        }
      });
    }
    return module;
  }

  @Override
  public String getPlatformTarget() {
    return "android";
  }

  @Override
  public TreeComponentDecorator getTreeDecorator() {
    return myTreeDecorator;
  }

  @Override
  public WrapInProvider getWrapInProvider() {
    if (myWrapInProvider == null) {
      myWrapInProvider = new AndroidWrapInProvider(getProject());
    }
    return myWrapInProvider;
  }

  @Override
  protected ComponentDecorator getRootSelectionDecorator() {
    return EmptyComponentDecorator.INSTANCE;
  }

  @Override
  public List<PaletteGroup> getPaletteGroups() {
    return ViewsMetaManager.getInstance(getProject()).getPaletteGroups();
  }

  @NotNull
  @Override
  public String getVersionLabel(@Nullable String version) {
    if (StringUtil.isEmpty(version)) {
      return "";
    }

    // Android versions are recorded as API integers
    Integer api = Ints.tryParse(version);
    assert api != null : version;
    int since = api.intValue();
    if (since <= 1) {
      return "";
    }

    String name = SdkVersionInfo.getAndroidName(since);

    if (name == null) {
      name = String.format("API %1$d", since);
    }

    return name;
  }

  @Override
  public boolean isDeprecated(@Nullable String deprecatedIn) {
    if (deprecatedIn == null) {
      return false;
    }

    IAndroidTarget target = myConfiguration != null ? myConfiguration.getTarget() : null;
    if (target == null) {
      return super.isDeprecated(deprecatedIn);
    }

    if (StringUtil.isEmpty(deprecatedIn)) {
      return false;
    }

    Integer api = Ints.tryParse(deprecatedIn);
    assert api != null : deprecatedIn;
    return api.intValue() <= target.getVersion().getApiLevel();
  }

  @Override
  @NotNull
  protected ComponentCreationFactory createCreationFactory(final PaletteItem paletteItem) {
    return new ComponentCreationFactory() {
      @NotNull
      @Override
      public RadComponent create() throws Exception {
        RadViewComponent component = ModelParser.createComponent(null, paletteItem.getMetaModel());
        component.setInitialPaletteItem(paletteItem);
        if (component instanceof IConfigurableComponent) {
          ((IConfigurableComponent)component).configure(myRootComponent);
        }
        return component;
      }
    };
  }

  @Override
  public ComponentPasteFactory createPasteFactory(String xmlComponents) {
    if (myConfiguration != null) {
      IAndroidTarget target = myConfiguration.getTarget();
      if (target != null) {
        return new AndroidPasteFactory(getModule(), target, xmlComponents);
      }
    }

    return null;
  }

  private void updatePalette(IAndroidTarget target) {
    try {
      for (PaletteGroup group : getPaletteGroups()) {
        for (PaletteItem item : group.getItems()) {
          String version = item.getVersion();
          if (version != null) {
            Integer api = Ints.tryParse(version);
            assert api != null : version;
            DefaultPaletteItem paletteItem = (DefaultPaletteItem)item;
            paletteItem.setEnabled(api.intValue() <= target.getVersion().getApiLevel());
          }
        }
      }

      PaletteItem item = getActivePaletteItem();
      if (item != null && !item.isEnabled()) {
        activatePaletteItem(null);
      }

      PaletteToolWindowManager.getInstance(this).refresh();
    }
    catch (Throwable e) {
      // Pass
    }
  }

  @Override
  public String getEditorText() {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return myXmlFile.getText();
      }
    });
  }

  @Override
  protected boolean execute(ThrowableRunnable<Exception> operation, boolean updateProperties) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return false;
    }
    try {
      myPsiChangeListener.stop();
      operation.run();
      updateRenderer(updateProperties);
      return true;
    }
    catch (Throwable e) {
      showError("Execute command", e);
      return false;
    }
    finally {
      myPsiChangeListener.start();
    }
  }

  @Override
  protected void executeWithReparse(ThrowableRunnable<Exception> operation) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return;
    }
    try {
      myPsiChangeListener.stop();
      operation.run();
      myPsiChangeListener.start();
      reparseFile();
    }
    catch (Throwable e) {
      showError("Execute command", e);
      myPsiChangeListener.start();
    }
  }

  @Override
  protected void execute(List<EditOperation> operations) {
    if (!ReadonlyStatusHandler.ensureFilesWritable(getProject(), myFile)) {
      return;
    }
    try {
      myPsiChangeListener.stop();
      for (EditOperation operation : operations) {
        operation.execute();
      }
      updateRenderer(true);
    }
    catch (Throwable e) {
      showError("Execute command", e);
    }
    finally {
      myPsiChangeListener.start();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Inspections
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void loadInspections(ProgressIndicator progress) {
    if (myRootComponent != null) {
      ErrorAnalyzer.load(getProject(), myXmlFile, myRootComponent, progress);
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static class MyThrowable extends Throwable {
    public Throwable original;

    public MyThrowable wrap(Throwable original) {
      this.original = original;
      return this;
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Zooming
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private static final double ZOOM_FACTOR = 1.2;

  public boolean isZoomToFit() {
    return myZoomMode == ZoomType.FIT || myZoomMode == ZoomType.FIT_INTO;
  }

  @Override
  public boolean isZoomSupported() {
    return true;
  }

  /** Sets the zoom level. Note that this should be 1, not 100 (percent), for an image at its actual size */
  @Override
  public void setZoom(double zoom) {
    if (myMaxWidth > 0 && myRootComponent != null) {
      // If we have a fixed size, ignore scale factor
      assert myMaxHeight > 0;
      Rectangle bounds = myRootComponent.getBounds();
      double imageWidth = bounds.getWidth();
      double imageHeight = bounds.getHeight();
      if (imageHeight > 0) {
        zoom = Math.min(myMaxWidth / imageWidth, myMaxHeight / imageHeight);
      }
    }

    if (zoom != myZoom) {
      myZoom = zoom;
      normalizeScale();
      viewZoomed();
      mySurfaceArea.scrollToSelection();
      repaint();
    }
  }

  private void normalizeScale() {
    // Some operations are faster if the zoom is EXACTLY 1.0 rather than ALMOST 1.0.
    // (This is because there is a fast-path when image copying and the scale is 1.0;
    // in that case it does not have to do any scaling).
    //
    // If you zoom out 10 times and then back in 10 times, small rounding errors mean
    // that you end up with a scale=1.0000000000000004. In the cases, when you get close
    // to 1.0, just make the zoom an exact 1.0.
    if (Math.abs(myZoom - 1.0) < 0.01) {
      myZoom = 1.0;
    }
  }

  /** Returns the current zoom level. Note that this is 1, not 100 (percent) for an image at its actual size */
  @Override
  public double getZoom() {
    return myZoom;
  }

  private int myMaxWidth;
  private int myMaxHeight;
  private boolean myUseLargeShadows = true;

  public boolean isUseLargeShadows() {
    return myUseLargeShadows;
  }

  /** Zooms the designer view */
  @Override
  public void zoom(@NotNull ZoomType type) {
    myZoomMode = type;
    updateZoom();
  }

  private void updateZoom() {
    switch (myZoomMode) {
      case IN:
        setZoom(myZoom * ZOOM_FACTOR);
        break;
      case OUT:
        setZoom(myZoom / ZOOM_FACTOR);
        break;
      case ACTUAL:
        setZoom(1);
        break;
      case FIT_INTO:
      case FIT: {
        Dimension sceneSize = myRootComponent.getBounds().getSize();
        Dimension screenSize = getDesignerViewSize();
        if (screenSize.width > 0 && screenSize.height > 0) {
          int sceneWidth = sceneSize.width;
          int sceneHeight = sceneSize.height;
          if (sceneWidth > 0 && sceneHeight > 0) {
            int viewWidth = screenSize.width;
            int viewHeight = screenSize.height;

            // Reduce the margins if necessary
            int hDelta = viewWidth - sceneWidth;
            int xMargin = 0;
            if (hDelta > 2 * DEFAULT_HORIZONTAL_MARGIN) {
              xMargin = DEFAULT_HORIZONTAL_MARGIN;
            } else if (hDelta > 0) {
              xMargin = hDelta / 2;
            }

            int vDelta = viewHeight - sceneHeight;
            int yMargin = 0;
            if (vDelta > 2 * DEFAULT_VERTICAL_MARGIN) {
              yMargin = DEFAULT_VERTICAL_MARGIN;
            } else if (vDelta > 0) {
              yMargin = vDelta / 2;
            }

            double hScale = (viewWidth - 2 * xMargin) / (double) sceneWidth;
            double vScale = (viewHeight - 2 * yMargin) / (double) sceneHeight;
            double scale = Math.min(hScale, vScale);

            if (myZoomMode == FIT_INTO) {
              scale = Math.min(1.0, scale);
            }

            setZoom(scale);
          }
        }
        break;
      }
      case SCREEN:
      default:
        throw new UnsupportedOperationException("Not yet implemented: " + myZoomMode);
    }
  }

  @NotNull
  private Dimension getDesignerViewSize() {
    Dimension size = myScrollPane.getSize();
    size.width -= 2;
    size.height -= 2;

    RootView rootView = getRootView();
    if (rootView != null) {
      if (rootView.getShowDropShadow()) {
        size.width -= ShadowPainter.SHADOW_SIZE;
        size.height -= ShadowPainter.SHADOW_SIZE;
      }

      final int MIN_SIZE = 200;
      if (myPreviewManager != null && size.width > MIN_SIZE) {
        int previewWidth  = myPreviewManager.computePreviewWidth();
        size.width = Math.max(MIN_SIZE, size.width - previewWidth);
      }
    }

    return size;
  }

  @Override
  @NotNull
  protected Dimension getSceneSize(@NotNull Component target) {
    int width = 0;
    int height = 0;

    if (myRootComponent != null) {
      Rectangle bounds = myRootComponent.getBounds(target);
      width = Math.max(width, (int)bounds.getMaxX());
      height = Math.max(height, (int)bounds.getMaxY());

      width += 1;
      height += 1;

      return new Dimension(width, height);
    }

    return super.getSceneSize(target);
  }

  @Override
  protected void viewZoomed() {
    RootView rootView = getRootView();
    if (rootView != null) {
      rootView.updateSize();
    }
    revalidate();
    super.viewZoomed();
  }


  @Nullable
  private RootView getRootView() {
    if (myRootComponent instanceof RadViewComponent) {
      Component nativeComponent = ((RadViewComponent)myRootComponent).getNativeComponent();
      if (nativeComponent instanceof RootView) {
        return (RootView)nativeComponent;
      }
    }
    return null;
  }

  @Nullable
  private RadViewComponent getLayoutRoot() {
    if (myRootComponent != null && myRootComponent.getChildren().size() == 1) {
      RadComponent component = myRootComponent.getChildren().get(0);
      if (component.isBackground() && component instanceof RadViewComponent) {
        return (RadViewComponent)component;
      }
    }

    return null;
  }

  @Nullable
  @Override
  protected RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
    RadComponent target = super.findTarget(x, y, filter);

    // If you click/drag outside the root, select the root
    if (target == null) {
      target = getLayoutRoot();
      if (target != null && filter != null && filter.preFilter(myRootComponent)) {
        filter.resultFilter(target);
      }
    }

    return target;
  }

  /**
   * Layered pane which shows the rendered image, as well as (if applicable) an error message panel on top of the rendering
   * near the bottom
   */
  private static class MyRenderPanelWrapper extends JPanel {
    private final RenderErrorPanel myErrorPanel;
    private int myErrorPanelHeight = -1;

    public MyRenderPanelWrapper(@NotNull RenderErrorPanel errorPanel) {
      super(new BorderLayout());
      myErrorPanel = errorPanel;
      setBackground(null);
      setOpaque(false);
      add(errorPanel);
    }

    private RenderErrorPanel getErrorPanel() {
      return myErrorPanel;
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();
    }

    private void positionErrorPanel() {
      int height = getHeight();
      int width = getWidth();
      int size;
      if (SIZE_ERROR_PANEL_DYNAMICALLY) {
        if (myErrorPanelHeight == -1) {
          // Make the layout take up to 3/4ths of the height, and at least 1/4th, but
          // anywhere in between based on what the actual text requires
          size = height * 3 / 4;
          int preferredHeight = myErrorPanel.getPreferredHeight(width) + 8;
          if (preferredHeight < size) {
            size = Math.max(preferredHeight, Math.min(height / 4, size));
            myErrorPanelHeight = size;
          }
        } else {
          size = myErrorPanelHeight;
        }
      } else {
        size = height / 2;
      }

      myErrorPanel.setSize(width, size);
      myErrorPanel.setLocation(0, height - size);
    }
  }

  private void saveState() {
    if (myConfiguration != null) {
      myConfiguration.save();
    }
  }

  // ---- Implements RenderContext ----

  @Override
  @Nullable
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Override
  public void setConfiguration(@NotNull Configuration configuration) {
    if (configuration != myConfiguration) {
      if (myConfiguration != null) {
        myConfiguration.removeListener(myConfigListener);
      }
      myConfiguration = configuration;
      myConfiguration.addListener(myConfigListener);
      myConfigListener.changed(MASK_ALL);
      // TODO: Cause immediate toolbar updates?
    }
  }

  @Override
  public void requestRender() {
    updateRenderer(false);
  }

  @Override
  @NotNull
  public UsageType getType() {
    return UsageType.LAYOUT_EDITOR;
  }

  @NotNull
  @Override
  public XmlFile getXmlFile() {
    return myXmlFile;
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  @Override
  public boolean hasAlphaChannel() {
    return !myRootView.getShowDropShadow();
  }

  @Override
  @NotNull
  public Component getComponent() {
    return myLayeredPane;
  }

  @Override
  public void updateLayout() {
    zoom(ZoomType.FIT);
    Component component = getComponent();
    if (component instanceof JComponent) {
      JComponent jc = (JComponent)component;
      jc.revalidate();
    } else {
      component.validate();
    }
    if (myRootView != null) {
      ((JComponent)myRootView.getParent()).revalidate();
    }
    component.repaint();
  }

  @Override
  public void setDeviceFramesEnabled(boolean on) {
    // TODO
  }

  @Nullable
  @Override
  public BufferedImage getRenderedImage() {
    return myRootView != null ? myRootView.getImage() : null;
  }

  @Override
  @NotNull
  public Dimension getFullImageSize() {
    if (myRootView != null) {
      BufferedImage image = myRootView.getImage();
      if (image != null) {
        return new Dimension(image.getWidth(), image.getHeight());
      }
    }

    return NO_SIZE;
  }

  @Override
  @NotNull
  public Dimension getScaledImageSize() {
    if (myRootView != null) {
      BufferedImage image = myRootView.getImage();
      if (image != null) {
        return new Dimension((int)(myZoom * image.getWidth()), (int)(myZoom * image.getHeight()));
      }
    }

    return NO_SIZE;
  }

  @Override
  public void setMaxSize(int width, int height) {
    myMaxWidth = width;
    myMaxHeight = height;
    myUseLargeShadows = width <= 0;
  }

  @Override
  public void zoomFit(boolean onlyZoomOut, boolean allowZoomIn) {
    zoom(allowZoomIn ? ZoomType.FIT : ZoomType.FIT_INTO);
  }

  @Override
  @NotNull
  public Rectangle getClientArea() {
    return myScrollPane.getViewport().getViewRect();
  }

  @Override
  public boolean supportsPreviews() {
    return true;
  }

  @Nullable
  @Override
  public RenderPreviewManager getPreviewManager(boolean createIfNecessary) {
    if (myPreviewManager == null && createIfNecessary) {
      myPreviewManager = new RenderPreviewManager(this);
      RenderPreviewPanel panel = new RenderPreviewPanel();
      myLayeredPane.add(panel, LAYER_PREVIEW);
      myLayeredPane.revalidate();
      myLayeredPane.repaint();
    }

    return myPreviewManager;
  }


  // ---- Implements BuildVariantSelectionChangeListener ----

  @Override
  public void buildVariantSelected(@NotNull AndroidFacet facet) {
    if (facet == myFacet) {
      if (myActive) {
        // The project resources should already have been refreshed by their own variant listener
        updateRenderer(true);
      } else {
        myVariantChanged = true;
      }
    }
  }

  private class LayoutConfigurationListener implements ConfigurationListener {
    @Override
    public boolean changed(int flags) {
      if (isProjectClosed()) {
        return true;
      }

      if (myActive) {
        updateRenderer(false);

        if ((flags & CFG_TARGET) != 0) {
          IAndroidTarget target = myConfiguration != null ? myConfiguration.getTarget() : null;
          if (target != null) {
            updatePalette(target);
          }
        }

        saveState();
      } else {
        myConfigurationDirty |= flags;
      }

      return true;
    }
  }

  private class RenderPreviewPanel extends JComponent {
    RenderPreviewPanel() {
      //super(new BorderLayout());
      setBackground(null);
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (myPreviewManager != null) {
        myPreviewManager.paint((Graphics2D)g);
      }
    }
  }

  @Override
  protected DesignerEditableArea createEditableArea() {
    return new AndroidEditableArea();
  }

  private class AndroidEditableArea extends DesignerEditableArea {
    @Override
    public InputTool findTargetTool(int x, int y) {
      if (myPreviewManager != null && myRootView != null) {
        if (myPreviewTool == null) {
          myPreviewTool = new RenderPreviewTool();
        }

        if (x > (myRootView.getX() + myRootView.getWidth()) ||
            y > (myRootView.getY() + myRootView.getHeight())) {
          return myPreviewTool;
        }
      }

      return super.findTargetTool(x, y);
    }
  }

  private class RenderPreviewTool extends InputTool {
    @Override
    public void mouseMove(MouseEvent event, EditableArea area) throws Exception {
      if (myPreviewManager != null) {
        myPreviewManager.moved(event);
      }
    }

    @Override
    public void mouseUp(MouseEvent event, EditableArea area) throws Exception {
      super.mouseUp(event, area);
      if (myPreviewManager != null && event.getClickCount() > 0) {
        myPreviewManager.click(event);
      }
    }

    @Override
    public void mouseEntered(MouseEvent event, EditableArea area) throws Exception {
      super.mouseEntered(event, area);
      if (myPreviewManager != null) {
        myPreviewManager.enter(event);
      }
    }

    @Override
    public void mouseExited(MouseEvent event, EditableArea area) throws Exception {
      super.mouseExited(event, area);
      if (myPreviewManager != null) {
        myPreviewManager.exit(event);
      }
    }
  }
}
