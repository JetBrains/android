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

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.actions.OverrideResourceAction;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.tools.idea.rendering.multi.RenderPreviewManager.SUPPORTS_MANUAL_PREVIEWS;

public class ConfigurationMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public ConfigurationMenuAction(RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Configuration to render this layout with inside the IDE");
    if (RenderService.NELE_ENABLED) {
      presentation.setText("Preview");
      presentation.setIcon(AndroidIcons.NeleIcons.Preview);
    } else {
      presentation.setIcon(AndroidIcons.AndroidFile);
    }
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
          group.add(new SwitchToVariationAction(title, project, file, virtualFile.equals(file)));
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
    addRtlPreviewAction(myRenderContext, group);
    addApiLevelPreviewAction(myRenderContext, group);

    // TODO: Support included layouts
    boolean DISABLE_RENDER_INCLUDED = true;

    boolean canPreviewIncluded = !DISABLE_RENDER_INCLUDED && hasCapability(myRenderContext, Features.EMBEDDED_LAYOUT);
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

  static void addRtlPreviewAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group) {
    boolean enabled = hasCapability(context, Features.RTL);
    group.add(new PreviewAction(context, "Preview Right-to-Left Layout", ACTION_PREVIEW_MODE, RenderPreviewMode.RTL, enabled));
  }

  static void addApiLevelPreviewAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group) {
    boolean enabled = hasCapability(context, Features.SIMULATE_PLATFORM);
    group.add(new PreviewAction(context, "Preview Android Versions", ACTION_PREVIEW_MODE, RenderPreviewMode.API_LEVELS, enabled));
  }

  private static boolean hasCapability(RenderContext context, int capability) {
    boolean enabled = false;
    Configuration configuration = context.getConfiguration();
    Module module = context.getModule();
    if (configuration != null && module != null) {
      IAndroidTarget target = configuration.getTarget();
      if (target != null) {
        LayoutLibrary library = RenderService.getLayoutLibrary(module, target);
        enabled = library != null && library.supports(capability);
      }
    }
    return enabled;
  }

  static void addScreenSizeAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group) {
    boolean enabled = context.supportsPreviews();
    group.add(new PreviewAction(context, "Preview All Screen Sizes", ACTION_PREVIEW_MODE, RenderPreviewMode.SCREENS, enabled));
  }

  static void addRemovePreviewsAction(@NotNull RenderContext context, @NotNull DefaultActionGroup group) {
    boolean enabled = context.supportsPreviews();
    group.add(new PreviewAction(context, "Remove Previews", ACTION_PREVIEW_MODE, RenderPreviewMode.NONE, enabled));
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
      OverrideResourceAction.forkResourceFile(myRenderContext, myNewFolder, true);
    }
  }
}
