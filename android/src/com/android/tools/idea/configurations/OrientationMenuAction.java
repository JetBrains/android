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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier;
import com.android.ide.common.resources.configuration.SmallestScreenWidthQualifier;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.actions.DropDownAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Toggleable;
import org.jetbrains.android.intentions.OverrideResourceAction;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.FD_RES_LAYOUT;

public class OrientationMenuAction extends DropDownAction {
  private final ConfigurationHolder myRenderContext;
  private final EditorDesignSurface mySurface;

  /**
   * Create a Menu to switch the orientation of the preview.
   * If an {@link EditorDesignSurface} is provided, actions to create layout for
   * different variation of the layout will be created
   *
   * @param renderContext The render context to get the configuration
   * @param surface       The current {@link EditorDesignSurface} where this action is display
   *                      used to create the variation.
   * @see #createVariationsActions(Configuration, EditorDesignSurface)
   */
  // TODO The surface is probably no needed, createVariationAction should be able to use the renderContext configuration
  public OrientationMenuAction(@NotNull ConfigurationHolder renderContext, @Nullable EditorDesignSurface surface) {
    super("Orientation for Preview", "Orientation for Preview", StudioIcons.LayoutEditor.Toolbar.ROTATE_BUTTON);
    myRenderContext = renderContext;
    mySurface = surface;
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    removeAll();
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      Device device = configuration.getCachedDevice();
      if (device != null) {
        State currentDeviceState = configuration.getDeviceState();

        // Do not allow to change the orientation of the wear devices.
        //noinspection SimplifiableConditionalExpression
        boolean showSetOrientationOptions = StudioFlags.NELE_WEAR_DEVICE_FIXED_ORIENTATION.get() ? !HardwareConfigHelper.isWear(device)
                                                                                                 : true;

        if (showSetOrientationOptions) {
          List<State> states = device.getAllStates();
          for (State state : states) {
            String stateName = state.getName();
            String title = stateName;

            VirtualFile better = ConfigurationMatcher.getBetterMatch(configuration, null, stateName, null, null);
            if (better != null) {
              title = ConfigurationAction.getBetterMatchLabel(stateName, better, configuration.getFile());
            }

            SetDeviceStateAction action = new SetDeviceStateAction(myRenderContext, title, state, state == currentDeviceState);
            add(action);
          }
        }
      }

      addSeparator();
      DefaultActionGroup uiModeGroup = DefaultActionGroup.createPopupGroup(() -> "_UI Mode");
      UiMode currentUiMode = configuration.getUiMode();
      for (UiMode uiMode : UiMode.values()) {
        String title = uiMode.getShortDisplayValue();
        boolean checked = uiMode == currentUiMode;
        uiModeGroup.add(new SetUiModeAction(myRenderContext, title, uiMode, checked));
      }
      add(uiModeGroup);

      if (mySurface != null) {
        addSeparator();
        createVariationsActions(configuration, mySurface);
      }
    }
    return true;
  }

  private void createVariationsActions(@NotNull Configuration configuration, @NotNull EditorDesignSurface surface) {
    VirtualFile virtualFile = configuration.getFile();
    if (virtualFile != null) {
      Module module = configuration.getModule();
      if (module == null) {
        return;
      }
      Project project = module.getProject();

      List<VirtualFile> variations = IdeResourcesUtil.getResourceVariations(virtualFile, true);
      if (variations.size() > 1) {
        for (VirtualFile file : variations) {
          String title = String.format("Switch to %1$s", file.getParent().getName());
          add(new SwitchToVariationAction(title, project, file, virtualFile.equals(file)));
        }
        addSeparator();
      }

      ResourceFolderType folderType = IdeResourcesUtil.getFolderType(configuration.getFile());
      if (folderType == ResourceFolderType.LAYOUT) {
        boolean haveLandscape = false;
        boolean haveTablet = false;
        for (VirtualFile file : variations) {
          String name = file.getParent().getName();
          if (name.startsWith(FD_RES_LAYOUT)) {
            FolderConfiguration config = FolderConfiguration.getConfigForFolder(name);
            if (config != null) {
              ScreenOrientationQualifier orientation = config.getScreenOrientationQualifier();
              if (orientation != null && orientation.getValue() == ScreenOrientation.LANDSCAPE) {
                haveLandscape = true;
                if (haveTablet) {
                  break;
                }
              }
              SmallestScreenWidthQualifier size = config.getSmallestScreenWidthQualifier();
              if (size != null && size.getValue() >= 600) {
                haveTablet = true;
                if (haveLandscape) {
                  break;
                }
              }
            }
          }
        }

        // Create actions for creating "common" versions of a layout (that don't exist),
        // e.g. Create Landscape Version, Create RTL Version, Create tablet version
        // Do statistics on what is needed!
        if (!haveLandscape) {
          add(new CreateVariationAction(surface, "Create Landscape Variation", "layout-land"));
        }
        if (!haveTablet) {
          add(new CreateVariationAction(surface, "Create Tablet Variation", "layout-sw600dp"));
        }
        add(new CreateVariationAction(surface, "Create Other...", null));
      }
      else {
        add(new CreateVariationAction(surface, "Create Alternative...", null));
      }
    }
  }

  @NotNull
  public static ScreenOrientation getOrientation(@NotNull State state) {
    FolderConfiguration config = DeviceConfigHelper.getFolderConfig(state);
    ScreenOrientation orientation = null;
    if (config != null && config.getScreenOrientationQualifier() != null) {
      orientation = config.getScreenOrientationQualifier().getValue();
    }

    if (orientation == null) {
      orientation = ScreenOrientation.PORTRAIT;
    }

    return orientation;
  }

  @VisibleForTesting
  static class SetDeviceStateAction extends ConfigurationAction {
    @NotNull private final State myState;

    private SetDeviceStateAction(@NotNull ConfigurationHolder renderContext,
                                 @NotNull String title,
                                 @NotNull State state,
                                 boolean isCurrentState) {
      super(renderContext, title);
      myState = state;
      getTemplatePresentation().putClientProperty(SELECTED_PROPERTY, isCurrentState);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setDeviceState(myState);
      if (!HardwareConfigHelper.isWear(configuration.getDevice())) {
        // Save the last orientation if device is not a wear device.
        ConfigurationProjectState projectState = configuration.getConfigurationManager().getStateManager().getProjectState();
        projectState.setNonWearDeviceLastStateName(myState.getName());
      }
    }
  }

  private static class SetUiModeAction extends ConfigurationAction {
    @NotNull private final UiMode myUiMode;

    private SetUiModeAction(@NotNull ConfigurationHolder renderContext, @NotNull String title, @NotNull UiMode uiMode, boolean checked) {
      super(renderContext, title);
      myUiMode = uiMode;
      getTemplatePresentation().putClientProperty(SELECTED_PROPERTY, checked);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setUiMode(myUiMode);
    }
  }

  @VisibleForTesting
  static class SwitchToVariationAction extends AnAction implements Toggleable {
    private final Project myProject;
    private final VirtualFile myFile;

    public SwitchToVariationAction(@NotNull String title, @NotNull Project project, @NotNull VirtualFile file, boolean select) {
      super(title, null, null);
      myFile = file;
      myProject = project;
      if (select) {
        Presentation templatePresentation = getTemplatePresentation();
        templatePresentation.putClientProperty(SELECTED_PROPERTY, true);
        templatePresentation.setEnabled(false);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, myFile, -1);
      FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
    }
  }

  @VisibleForTesting
  static class CreateVariationAction extends AnAction {
    @NotNull private EditorDesignSurface mySurface;
    @Nullable private String myNewFolder;

    public CreateVariationAction(@NotNull EditorDesignSurface surface, @NotNull String title, @Nullable String newFolder) {
      super(title, null, null);
      mySurface = surface;
      myNewFolder = newFolder;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      OverrideResourceAction.forkResourceFile(mySurface, myNewFolder, true);
    }
  }
}
