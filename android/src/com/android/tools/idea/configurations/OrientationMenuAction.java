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
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OrientationMenuAction extends DropDownAction {
  private final ConfigurationHolder myRenderContext;
  private final EditorDesignSurface mySurface;

  /**
   * Create a Menu to switch the orientation and UI mode of the preview.
   *
   * @param renderContext The render context to get the configuration
   * @param surface       The current {@link EditorDesignSurface} where this action is display
   *                      used to create the variation.
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
    }
    return true;
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
      Toggleable.setSelected(getTemplatePresentation(), isCurrentState);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setDeviceState(myState);
      if (!HardwareConfigHelper.isWear(configuration.getDevice())) {
        // Save the last orientation if device is not a wear device.
        ConfigurationProjectState projectState = configuration.getConfigurationManager().getStateManager().getProjectState();
        projectState.setNonWearDeviceLastSelectedStateName(myState.getName(), myState.isDefaultState());
      }
    }
  }

  private static class SetUiModeAction extends ConfigurationAction {
    @NotNull private final UiMode myUiMode;

    private SetUiModeAction(@NotNull ConfigurationHolder renderContext, @NotNull String title, @NotNull UiMode uiMode, boolean checked) {
      super(renderContext, title);
      myUiMode = uiMode;
      Toggleable.setSelected(getTemplatePresentation(), checked);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setUiMode(myUiMode);
    }
  }
}
