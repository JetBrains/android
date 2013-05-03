/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.utils.Pair;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import icons.AndroidIcons;
import org.jetbrains.android.actions.CreateResourceDirectoryDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;

import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.tools.idea.rendering.multi.RenderPreviewManager.SUPPORTS_MANUAL_PREVIEWS;

public class ConfigurationMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public ConfigurationMenuAction(RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Configuration to render this layout with in the IDE");
    presentation.setIcon(AndroidIcons.AndroidFile);
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup("Configuration", true);

    VirtualFile virtualFile = myRenderContext.getVirtualFile();
    if (virtualFile != null) {
      Module module = myRenderContext.getModule();
      if (module == null) {
        return group;
      }
      Project project = module.getProject();

      List<VirtualFile> variations = ResourceHelper.getResourceVariations(virtualFile, true);
      if (variations.size() > 1) {
        for (VirtualFile file : variations) {
          String title = String.format("Switch to %1$s", file.getParent().getName());
          group.add(new SwitchToVariationAction(title, project, file, virtualFile == file));
        }
        group.addSeparator();
      }

      boolean haveLandscape = false;
      boolean haveLarge = false;
      for (VirtualFile file : variations) {
        String name = file.getParent().getName();
        if (name.startsWith(FD_RES_LAYOUT)) {
          FolderConfiguration config = FolderConfiguration.getConfigForFolder(name);
          if (config != null) {
            ScreenOrientationQualifier orientation = config.getScreenOrientationQualifier();
            if (orientation != null && orientation.getValue() == ScreenOrientation.LANDSCAPE) {
              haveLandscape = true;
              if (haveLarge) {
                break;
              }
            }
            ScreenSizeQualifier size = config.getScreenSizeQualifier();
            if (size != null && size.getValue() == ScreenSize.XLARGE) {
              haveLarge = true;
              if (haveLandscape) {
                break;
              }
            }
          }
        }
      }

      // Create actions for creating "common" versions of a layout (that don't exist),
      // e.g. Create Landscape Version, Create RTL Version, Create XLarge version
      // Do statistics on what is needed!
      if (!haveLandscape) {
        group.add(new CreateVariationAction(myRenderContext, "Create Landscape Variation", "layout-land"));
      }
      if (!haveLarge) {
        group.add(new CreateVariationAction(myRenderContext, "Create layout-xlarge Variation", "layout-xlarge"));
        //group.add(new CreateVariationAction(myRenderContext, "Create layout-sw600dp Variation...", "layout-sw600dp"));
      }
      group.add(new CreateVariationAction(myRenderContext, "Create Other...", null));

      if (myRenderContext.supportsPreviews()) {
        addMultiConfigActions(group);
      }
    }

    return group;
  }

  private void addMultiConfigActions(DefaultActionGroup group) {
    VirtualFile file = myRenderContext.getVirtualFile();
    if (file == null) {
      return;
    }
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return;
    }
    ConfigurationManager configurationManager = configuration.getConfigurationManager();

    group.addSeparator();

    // Configuration Previews
    if (SUPPORTS_MANUAL_PREVIEWS) {
      group.add(new PreviewAction(myRenderContext, "Add As Thumbnail...", ACTION_ADD, null, true));
      RenderPreviewMode mode = RenderPreviewMode.getCurrent();
      if (mode == RenderPreviewMode.CUSTOM) {
        RenderPreviewManager previewManager = myRenderContext.getPreviewManager(false);
        boolean hasPreviews = previewManager != null && previewManager.hasManualPreviews();
        group.add(new PreviewAction(myRenderContext, "Delete All Thumbnails", ACTION_DELETE_ALL, null, hasPreviews));
      }
      group.addSeparator();
    }

    group.add(new PreviewAction(myRenderContext, "Preview Representative Sample", ACTION_PREVIEW_MODE, RenderPreviewMode.DEFAULT, true));

    addScreenSizeAction(myRenderContext, group);

    boolean haveMultipleLocales = configurationManager.getLocales().size() > 1;
    addLocalePreviewAction(myRenderContext, group, haveMultipleLocales);

    boolean canPreviewIncluded = false;
    // TODO: Support included layouts
    //canPreviewIncluded = includedBy != null && !includedBy.isEmpty();
    //if (!graphicalEditor.renderingSupports(Capability.EMBEDDED_LAYOUT)) {
    //    canPreviewIncluded = false;
    //}
    group.add(new PreviewAction(myRenderContext, "Preview Included", ACTION_PREVIEW_MODE, RenderPreviewMode.INCLUDES, canPreviewIncluded));
    List<VirtualFile> variations = ResourceHelper.getResourceVariations(file, true);
    group.add(new PreviewAction(myRenderContext, "Preview Layout Versions", ACTION_PREVIEW_MODE, RenderPreviewMode.VARIATIONS,
                                variations.size() > 1));
    if (SUPPORTS_MANUAL_PREVIEWS) {
      group.add(new PreviewAction(myRenderContext, "Manual Previews", ACTION_PREVIEW_MODE, RenderPreviewMode.CUSTOM, true));
    }

    group.add(new PreviewAction(myRenderContext, "None", ACTION_PREVIEW_MODE, RenderPreviewMode.NONE, true));

    // Debugging only
    group.addSeparator();
    group.add(new AnAction("Toggle Layout Mode") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        RenderPreviewManager.toggleLayoutMode(myRenderContext);
      }
    });

  }

  static void addLocalePreviewAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group, boolean enabled) {
    group.add(new PreviewAction(context, "Preview All Locales", ACTION_PREVIEW_MODE, RenderPreviewMode.LOCALES, enabled));
  }

  static void addScreenSizeAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group) {
    group.add(new PreviewAction(context, "Preview All Screen Sizes", ACTION_PREVIEW_MODE, RenderPreviewMode.SCREENS, true));
  }

  static void addRemovePreviewsAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group) {
    group.add(new PreviewAction(context, "Remove Previews", ACTION_PREVIEW_MODE, RenderPreviewMode.NONE, true));
  }

  private static final int ACTION_ADD = 1;
  private static final int ACTION_DELETE_ALL = 2;
  private static final int ACTION_PREVIEW_MODE = 3;

  private static class PreviewAction extends AnAction {
    private final int myAction;
    private final RenderPreviewMode myMode;
    private final RenderContext myRenderContext;

    public PreviewAction(@NotNull RenderContext renderContext, @NotNull String title, int action,
                         @Nullable RenderPreviewMode mode, boolean enabled) {
      super(title, null, null);
      myRenderContext = renderContext;
      myAction = action;
      myMode = mode;

      if (mode != null && mode == RenderPreviewMode.getCurrent()) {
        // Select
        Presentation templatePresentation = getTemplatePresentation();
        templatePresentation.setIcon(AllIcons.Actions.Checked);
        templatePresentation.setEnabled(false);
      }

      if (!enabled) {
        getTemplatePresentation().setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      RenderPreviewManager previewManager = myRenderContext.getPreviewManager(true);
      assert previewManager != null;
      switch (myAction) {
        case ACTION_ADD: {
          previewManager.addAsThumbnail();
          break;
        }
        case ACTION_PREVIEW_MODE: {
          assert myMode != null;
          previewManager.selectMode(myMode);
          break;
        }
        case ACTION_DELETE_ALL: {
          previewManager.deleteManualPreviews();
          break;
        }
        default: assert false : myAction;
      }

      myRenderContext.updateLayout();
      //myRenderContext.zoomFit(true /*onlyZoomOut*/, false /*allowZoomIn*/);
    }
  }

  private static class SwitchToVariationAction extends AnAction {
    private final Project myProject;
    private final VirtualFile myFile;

    public SwitchToVariationAction(String title, @NotNull Project project, VirtualFile file, boolean select) {
      super(title, null, null);
      myFile = file;
      myProject = project;
      if (select) {
        Presentation templatePresentation = getTemplatePresentation();
        templatePresentation.setIcon(AllIcons.Actions.Checked);
        templatePresentation.setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, myFile, -1);
      FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
    }
  }

  private static class CreateVariationAction extends AnAction {
    @NotNull private RenderContext myRenderContext;
    @Nullable private String myNewFolder;

    public CreateVariationAction(@NotNull RenderContext renderContext, @NotNull String title, @Nullable String newFolder) {
      super(title, null, null);
      myRenderContext = renderContext;
      myNewFolder = newFolder;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final VirtualFile file = myRenderContext.getVirtualFile();
      if (file == null) {
        assert false;
        return; // Should not happen
      }
      Module module = myRenderContext.getModule();
      if (module == null) {
        assert false;
        return; // Should not happen
      }
      final Project project = module.getProject();
      final FolderConfiguration folderConfig;
      if (myNewFolder == null) {
        // Open a file chooser to select the configuration to be created
        VirtualFile parentFolder = file.getParent();
        assert parentFolder != null;
        VirtualFile res = parentFolder.getParent();
        folderConfig = selectFolderConfig(project, res);
      }
      else {
        folderConfig = FolderConfiguration.getConfigForFolder(myNewFolder);
      }
      if (folderConfig == null) {
        return;
      }

      Pair<String, VirtualFile> result = ApplicationManager.getApplication().runWriteAction(new Computable<Pair<String, VirtualFile>>() {
        @Override
        public Pair<String, VirtualFile> compute() {
          String folderName = folderConfig.getFolderName(ResourceFolderType.LAYOUT);
          try {
            VirtualFile parentFolder = file.getParent();
            assert parentFolder != null;
            VirtualFile res = parentFolder.getParent();
            VirtualFile newParentFolder = res.findChild(folderName);
            if (newParentFolder == null) {
              newParentFolder = res.createChildDirectory(this, folderName);
              if (newParentFolder == null) {
                String message = String.format("Could not create folder %1$s in %2$s", folderName, res.getPath());
                return Pair.of(message, null);
              }
            }

            final VirtualFile existing = newParentFolder.findChild(file.getName());
            if (existing != null && existing.exists()) {
              String message = String.format("File 'res/%1$s/%2$s' already exists!", folderName, file.getName());
              return Pair.of(message, null);
            }

            // Attempt to get the document from the PSI file rather than the file on disk: get edited contents too
            String text;
            XmlFile xmlFile = myRenderContext.getXmlFile();
            if (xmlFile != null) {
              text = xmlFile.getText();
            }
            else {
              text = StreamUtil.readText(file.getInputStream(), "UTF-8");
            }
            VirtualFile newFile = newParentFolder.createChildData(this, file.getName());
            VfsUtil.saveText(newFile, text);
            return Pair.of(null, newFile);
          }
          catch (IOException e2) {
            String message = String.format("Failed to create File 'res/%1$s/%2$s' : %3$s", folderName, file.getName(), e2.getMessage());
            return Pair.of(message, null);
          }
        }
      });

      String error = result.getFirst();
      VirtualFile newFile = result.getSecond();
      if (error != null) {
        Messages.showErrorDialog(project, error, "Create Layout");
      }
      else {
        // First create a compatible configuration based on the current configuration
        Configuration configuration = myRenderContext.getConfiguration();
        assert configuration != null;
        ConfigurationManager configurationManager = configuration.getConfigurationManager();
        configurationManager.createSimilar(newFile, file);

        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, newFile, -1);
        FileEditorManager.getInstance(project).openEditor(descriptor, true);
      }
    }
  }

  @Nullable
  private static FolderConfiguration selectFolderConfig(final Project project, VirtualFile res) {
    final PsiDirectory directory = PsiManager.getInstance(project).findDirectory(res);
    if (directory == null) {
      return null;
    }
    final CreateResourceDirectoryDialog dialog = new CreateResourceDirectoryDialog(project, ResourceFolderType.LAYOUT) {
      @Override
      protected InputValidator createValidator() {
        return new ResourceDirectorySelector(project, directory);
      }
    };
    dialog.setTitle("Select Layout Directory");
    dialog.show();
    final InputValidator validator = dialog.getValidator();
    if (validator != null) {
      PsiElement[] createdElements = ((ResourceDirectorySelector)validator).getCreatedElements();
      if (createdElements != null && createdElements.length > 0) {
        PsiDirectory dir = (PsiDirectory)createdElements[0];
        return FolderConfiguration.getConfigForFolder(dir.getName());
      }
    }

    return null;
  }

  /**
   * Selects (and optionally creates) a layout resource directory
   */
  private static class ResourceDirectorySelector extends ElementCreator implements InputValidator {
    private final PsiDirectory myDirectory;
    private PsiElement[] myCreatedElements = PsiElement.EMPTY_ARRAY;

    public ResourceDirectorySelector(final Project project, final PsiDirectory directory) {
      super(project, "Select Layout Directory");
      myDirectory = directory;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return true;
    }

    @Override
    public PsiElement[] create(String newName) throws Exception {
      return new PsiElement[]{myDirectory.createSubdirectory(newName)};
    }

    @Override
    public String getActionName(String newName) {
      return "Select Layout Directory";
    }

    @Override
    public boolean canClose(final String inputString) {
      // Already exists: ok
      PsiDirectory subdirectory = myDirectory.findSubdirectory(inputString);
      if (subdirectory != null) {
        myCreatedElements = new PsiDirectory[]{subdirectory};
        return true;
      }
      myCreatedElements = tryCreate(inputString);
      return myCreatedElements.length > 0;
    }

    public final PsiElement[] getCreatedElements() {
      return myCreatedElements;
    }
  }
}
