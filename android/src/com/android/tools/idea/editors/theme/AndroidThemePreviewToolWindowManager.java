/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.preview.ThemePreviewComponent;
import com.android.tools.idea.editors.theme.qualifiers.RestrictedConfiguration;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.AndroidEditorSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * {@link ProjectComponent} that provides the Theme Preview window next to the XML editor.
 */
public class AndroidThemePreviewToolWindowManager implements ProjectComponent {
  private static final String TOOL_WINDOW_ID = "Theme Preview";

  private final CaretListener myCaretListener = new MyCaretListener();
  private final DocumentListener myDocumentListener = new MyDocumentListener();
  private final Project myProject;
  private final FileEditorManager myFileEditorManager;
  private volatile ToolWindow myToolWindow;
  private TextEditor myActiveEditor;
  private ThemePreviewComponent myPreviewPanel;
  private ThemeEditorContext myThemeEditorContext;
  private final MergingUpdateQueue myToolWindowUpdateQueue;
  private boolean myIsToolWindowVisible = false;
  private final Update myPreviewUpdate = new Update("ThemePreviewUpdate") {
    @Override
    public void run() {
      if (!myIsToolWindowVisible || myActiveEditor == null) {
        return;
      }

      ConfiguredThemeEditorStyle previewTheme;
      Document document = myActiveEditor.getEditor().getDocument();
      int offset = myActiveEditor.getEditor().getCaretModel().getOffset();

      // Reload all the themes that might have changed
      myThemeEditorContext.updateThemeResolver();
      previewTheme = getThemeAtEditorOffset(document, offset);

      if (previewTheme != null) {
        myThemeEditorContext.setCurrentTheme(previewTheme);
        Configuration configuration = myThemeEditorContext.getConfiguration();
        configuration.setTheme(previewTheme.getStyleResourceUrl());

        ResourceResolver resourceResolver = myThemeEditorContext.getResourceResolver();
        assert resourceResolver != null;

        if (myPreviewPanel == null) {
          // Add the content panel to the tool window
          ContentManager contentManager = myToolWindow.getContentManager();
          myPreviewPanel = new ThemePreviewComponent(myThemeEditorContext);
          Disposer.register(myProject, myPreviewPanel);
          Content content = contentManager.getFactory().createContent(myPreviewPanel, null, false);
          contentManager.addContent(content);
        }
        else {
          myPreviewPanel.reloadPreviewContents();
        }
        myPreviewPanel
          .setBackground(ThemeEditorUtils.getGoodContrastPreviewBackground(previewTheme, resourceResolver));

        myToolWindow.setTitle(String.format("[%1$s]", previewTheme.getName()));
      }
    }
  };

  private AndroidThemePreviewToolWindowManager(@NotNull final Project project, @NotNull FileEditorManager filedEditorManager) {
    myProject = project;
    myFileEditorManager = filedEditorManager;

    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new MyFileEditorManagerListener());

    myToolWindowUpdateQueue = new MergingUpdateQueue("android.style.preview", 150, true, null, project);
    myToolWindowUpdateQueue.setRestartTimerOnAdd(true);
    Disposer.register(project, myToolWindowUpdateQueue);
  }

  @Override
  public void projectOpened() {
    initToolWindow();

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      @Override
      public void run() {
        processFileEditorChange();
      }
    });
  }

  @Override
  public void projectClosed() {
    processFileEditorChange(null);
    // myPreviewPanel will be disposed when the project is disposed
    myPreviewPanel = null;
  }

  private void initToolWindow() {
    myToolWindow =
      ToolWindowManager.getInstance(myProject).registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, myProject, false);
    myToolWindow.setIcon(AndroidIcons.ThemesPreview);
    myToolWindow.setAvailable(false, null);
    myToolWindow.setAutoHide(false);

    // Add a listener so we only update the preview when it's visible
    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      @Override
      public void stateChanged() {
        if (myProject.isDisposed()) {
          return;
        }

        final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
        if (window != null && window.isAvailable()) {
          final boolean visible = window.isVisible();
          AndroidEditorSettings.getInstance().getGlobalState().setVisible(visible);

          if (visible && !myIsToolWindowVisible) {
            updatePreview();
          }
          myIsToolWindowVisible = visible;
        }
      }
    });
  }

  @NotNull
  @Override
  public String getComponentName() {
    return AndroidThemePreviewToolWindowManager.class.getSimpleName();
  }

  @Nullable("if there is no available configuration that would select the passed file")
  private static Configuration getBestConfiguration(@Nullable PsiFile psiFile) {
    Module module = psiFile != null ? ModuleUtilCore.findModuleForPsiElement(psiFile) : null;
    if (module == null) {
      return null;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    ConfigurationManager manager = facet.getConfigurationManager();

    List<VirtualFile> variations = ResourceHelper.getResourceVariations(virtualFile, false /*includeSelf*/);
    if (variations.isEmpty()) {
      return manager.getConfiguration(virtualFile);
    }

    // There is more than one resource folder available so make sure we select a configuration that only matches the current file.
    Collection<FolderConfiguration> incompatible = Collections2.transform(variations, new Function<VirtualFile, FolderConfiguration>() {
      @Nullable
      @Override
      public FolderConfiguration apply(VirtualFile input) {
        assert input != null;
        return ResourceHelper.getFolderConfiguration(input);
      }
    });

    FolderConfiguration selectedFileFolderConfiguration = ResourceHelper.getFolderConfiguration(psiFile);
    if (selectedFileFolderConfiguration == null) {
      // This folder probably has invalid qualifiers or they are in the wrong order
      return null;
    }

    RestrictedConfiguration restrictedConfiguration =
      RestrictedConfiguration.restrict(selectedFileFolderConfiguration, incompatible);

    if (restrictedConfiguration == null) {
      // Unable to create a configuration that only matches this file
      return null;
    }

    FolderConfiguration restricted = restrictedConfiguration.getAny();
    Configuration newConfiguration = Configuration.create(manager, virtualFile, null, restricted);

    VersionQualifier newVersionQualifier = restricted.getVersionQualifier();
    if (newVersionQualifier != null) {
      IAndroidTarget realTarget = manager.getHighestApiTarget() != null ? manager.getHighestApiTarget() : manager.getTarget();
      assert realTarget != null;
      newConfiguration.setTarget(new CompatibilityRenderTarget(realTarget, newVersionQualifier.getVersion(), null));
    }

    return newConfiguration;
  }

  /**
   * Method called when the current XML editor focus changes to a new one.
   *
   * @param newEditor the new editor, or null if no editor is selected.
   */
  private void processFileEditorChange(@Nullable final TextEditor newEditor) {
    if (myToolWindow == null) {
      return;
    }

    if (myActiveEditor == newEditor) {
      return;
    }

    myToolWindowUpdateQueue.cancelAllUpdates();
    if (myActiveEditor != null) {
      myActiveEditor.getEditor().getCaretModel().removeCaretListener(myCaretListener);
      myActiveEditor.getEditor().getDocument().removeDocumentListener(myDocumentListener);
      myActiveEditor = null;
    }

    boolean available = false;
    if (newEditor != null && isApplicableEditor(newEditor)) {
      myActiveEditor = newEditor;
      CaretModel caretModel = myActiveEditor.getEditor().getCaretModel();
      caretModel.addCaretListener(myCaretListener);
      Document document = myActiveEditor.getEditor().getDocument();
      document.addDocumentListener(myDocumentListener);

      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      Configuration configuration = getBestConfiguration(psiFile);
      if (configuration != null) {
        if (myThemeEditorContext == null) {
          myThemeEditorContext = new ThemeEditorContext(configuration);
        } else {
          myThemeEditorContext.setConfiguration(configuration);
        }

        // Check if there is a theme at the current offset before enabling the preview
        if (getThemeAtEditorOffset(document, caretModel.getOffset()) != null) {
          available = true;
          updatePreview();
        }
      }
    }

    myToolWindow.setAvailable(available, null);
  }

  /**
   * Method called when the current XML editor focus changes to a new one. This method will find the current active
   * one and will pass it to {@link #processFileEditorChange(TextEditor)}
   */
  private void processFileEditorChange() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    FileEditor[] fileEditors = myFileEditorManager.getSelectedEditors();
    if (fileEditors.length > 0 && fileEditors[0] instanceof TextEditor) {
      final TextEditor textEditor = (TextEditor)fileEditors[0];

      processFileEditorChange(textEditor);
    }
    else {
      processFileEditorChange(null);
    }
  }

  /**
   * Returns whether the passed {@link TextEditor} is an XML editor with a theme file open.
   */
  private boolean isApplicableEditor(@NotNull TextEditor editor) {
    final Document document = editor.getEditor().getDocument();
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

    return ThemeEditorProvider.isAndroidTheme(psiFile);
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  private void updatePreview() {
    myToolWindowUpdateQueue.queue(myPreviewUpdate);
  }

  /**
   * Finds the closest theme to the given offset position
   */
  @Nullable("if there is no theme at the given offset or the theme couldn't be loaded")
  private ConfiguredThemeEditorStyle getThemeAtEditorOffset(@NotNull Document document, int offset) {
    if (offset == -1 || myThemeEditorContext == null) {
      return null;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) {
      return null;
    }

    if (!(psiFile instanceof XmlFile)) {
      return null;
    }

    XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
    if (rootTag == null) {
      return null;
    }


    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, XmlTag.class, false);
    if (tag == null) {
      return null;
    }

    if (!SdkConstants.TAG_STYLE.equals(tag.getLocalName())) {
      tag = tag.getParentTag();
    }

    if (tag != null && SdkConstants.TAG_STYLE.equals(tag.getLocalName())) {
      String styleName =  tag.getAttributeValue(SdkConstants.ATTR_NAME);
      return styleName != null ? myThemeEditorContext.getThemeResolver().getTheme(styleName) : null;
    }

    return null;
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NonNull FileEditorManager source, @NonNull VirtualFile file) {
      processFileEditorChange();
    }

    @Override
    public void fileClosed(@NonNull FileEditorManager source, @NonNull VirtualFile file) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          processFileEditorChange();
        }
      }, myProject.getDisposed());
    }

    @Override
    public void selectionChanged(@NonNull FileEditorManagerEvent event) {
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

  /**
   * CaretListener that detects when we move to a different theme.
   */
  private class MyCaretListener extends CaretAdapter {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      if (e == null || e.getCaret() == null) {
        myToolWindow.setAvailable(false, null);
        return;
      }

      CaretModel caretModel = e.getCaret().getCaretModel();
      int offset = caretModel.getOffset();
      if (offset == -1) {
        myToolWindow.setAvailable(false, null);
        return;
      }

      Document document = e.getEditor().getDocument();
      ConfiguredThemeEditorStyle previewTheme = getThemeAtEditorOffset(document, offset);
      myToolWindow.setAvailable(previewTheme != null, null);
      if (previewTheme != null && !previewTheme.equals(myThemeEditorContext.getCurrentTheme())) {
        // A new theme was selected so update the preview
        updatePreview();
      }
    }
  }

  /**
   * The document listener detects when there's been a change in the XML content and issues a refresh of the preview panel
   */
  private class MyDocumentListener extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent event) {
      updatePreview();
    }
  }
}
