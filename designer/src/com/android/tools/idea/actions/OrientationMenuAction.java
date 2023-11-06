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
package com.android.tools.idea.actions;

import static com.android.tools.idea.actions.DesignerDataKeys.CONFIGURATIONS;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.ConfigurationProjectState;
import com.android.tools.idea.configurations.ConfigurationMatcher;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class OrientationMenuAction extends DropDownAction {
  private final boolean myIncludeUiMode;

  /**
   * Create a Menu to switch the orientation and UI mode of the preview.
   */
  public OrientationMenuAction(boolean includeUiMode) {
    super("Orientation for Preview", "Orientation for Preview", StudioIcons.LayoutEditor.Toolbar.ROTATE_BUTTON);
    myIncludeUiMode = includeUiMode;
  }

  public void updateActionsImmediately(@NotNull DataContext dataContext) {
    updateActions(dataContext);
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    removeAll();
    Collection<Configuration> configurations = context.getData(CONFIGURATIONS);
    if (configurations == null) {
      return true;
    }
    Configuration configuration = Iterables.getFirst(configurations, null);
    if (configuration != null) {
      Device device = configuration.getCachedDevice();
      if (device != null) {
        State currentDeviceState = configuration.getDeviceState();

        // Do not allow to change the orientation of the wear devices.
        //noinspection SimplifiableConditionalExpression
        boolean showSetOrientationOptions = !HardwareConfigHelper.isWear(device);

        if (showSetOrientationOptions) {
          List<State> states = device.getAllStates();
          for (State state : states) {
            String stateName = state.getName();
            String title = stateName;

            VirtualFile better = ConfigurationMatcher.getBetterMatch(configuration, null, stateName, null, null);
            if (better != null) {
              title = ConfigurationAction.getBetterMatchLabel(stateName, better, configuration.getFile());
            }

            SetDeviceStateAction action = new SetDeviceStateAction(title, state, state == currentDeviceState);
            add(action);
          }
        }
      }

      if (myIncludeUiMode) {
        addSeparator();
        DefaultActionGroup uiModeGroup = createSubMenuGroup(() -> "_UI Mode");
        UiMode currentUiMode = configuration.getUiMode();
        for (UiMode uiMode : UiMode.values()) {
          String title = uiMode.getShortDisplayValue();
          boolean checked = uiMode == currentUiMode;
          uiModeGroup.add(new SetUiModeAction(title, uiMode, checked));
        }
        add(uiModeGroup);
      }
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

    private SetDeviceStateAction(@NotNull String title,
                                 @NotNull State state,
                                 boolean isCurrentState) {
      super(title);
      myState = state;
      Toggleable.setSelected(getTemplatePresentation(), isCurrentState);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setDeviceState(myState);
      if (!HardwareConfigHelper.isWear(configuration.getDevice())) {
        // Save the last orientation if device is not a wear device.
        ConfigurationProjectState projectState =
          configuration.getSettings().getConfigModule().getConfigurationStateManager().getProjectState();
        projectState.setNonWearDeviceLastSelectedStateName(myState.getName(), myState.isDefaultState());
      }
    }
  }

  private static class SetUiModeAction extends ConfigurationAction {
    @NotNull private final UiMode myUiMode;

    private SetUiModeAction(@NotNull String title, @NotNull UiMode uiMode, boolean checked) {
      super(title);
      myUiMode = uiMode;
      Toggleable.setSelected(getTemplatePresentation(), checked);
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setUiMode(myUiMode);
    }
  }
}
