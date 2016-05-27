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

import com.android.ide.common.resources.configuration.DeviceConfigHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.NightMode;
import com.android.resources.ScreenOrientation;
import com.android.resources.UiMode;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class OrientationMenuAction extends FlatComboAction {
  private final ConfigurationHolder myRenderContext;

  public OrientationMenuAction(ConfigurationHolder renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AndroidIcons.NeleIcons.Rotate);
  }

  @NotNull
  private static String getPresentationDescription(@NotNull State state) {
    return String.format("Switch to %1$s", state.getName());
  }

  @Override
  protected boolean handleIconClicked() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return false;
    }
    State current = configuration.getDeviceState();
    State flip = configuration.getNextDeviceState(current);
    if (flip != null) {
      SetDeviceStateAction action = new SetDeviceStateAction(myRenderContext, flip.getName(), flip, false, false);
      action.perform();
    }
    return true;
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      Device device = configuration.getDevice();
      if (device != null) {
        List<State> states = device.getAllStates();
        State current = configuration.getDeviceState();

        if (states.size() > 1 && current != null) {
          State flip = configuration.getNextDeviceState(current);
          State nextSate = flip == null ? current : flip;
          String title = getPresentationDescription(nextSate);
          group.add(new SetDeviceStateAction(myRenderContext, title, nextSate, false, true));
          group.addSeparator();
        }

        for (State config : states) {
          String stateName = config.getName();
          String title = stateName;

          VirtualFile better = ConfigurationMatcher.getBetterMatch(configuration, null, stateName, null, null);
          if (better != null) {
            title = ConfigurationAction.getBetterMatchLabel(stateName, better, configuration.getFile());
          }

          group.add(new SetDeviceStateAction(myRenderContext, title, config, config == current, false));
        }
        group.addSeparator();
      }

      group.addSeparator();
      DefaultActionGroup uiModeGroup = new DefaultActionGroup("_UI Mode", true);
      UiMode currentUiMode = configuration.getUiMode();
      for (UiMode uiMode : UiMode.values()) {
        String title = uiMode.getShortDisplayValue();
        boolean checked = uiMode == currentUiMode;
        uiModeGroup.add(new SetUiModeAction(myRenderContext, title, uiMode, checked));
      }
      group.add(uiModeGroup);

      group.addSeparator();
      DefaultActionGroup nightModeGroup = new DefaultActionGroup("_Night Mode", true);
      NightMode currentNightMode = configuration.getNightMode();
      for (NightMode nightMode : NightMode.values()) {
        String title = nightMode.getShortDisplayValue();
        boolean checked = nightMode == currentNightMode;
        nightModeGroup.add(new SetNightModeAction(myRenderContext, title, nightMode, checked));
      }
      group.add(nightModeGroup);
    }

    return group;
  }

  @NotNull
  public static Icon getOrientationIcon(@NotNull ScreenOrientation orientation, boolean flip) {
    switch (orientation) {
      case LANDSCAPE:
        return flip ? AndroidIcons.FlipLandscape : AndroidIcons.Landscape;
      case SQUARE:
        return AndroidIcons.Square;
      case PORTRAIT:
      default:
        return flip ? AndroidIcons.FlipPortrait : AndroidIcons.Portrait;
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


  private static class SetDeviceStateAction extends ConfigurationAction {
    @NotNull private final State myState;

    private SetDeviceStateAction(@NotNull ConfigurationHolder renderContext,
                                 @NotNull String title,
                                 @NotNull State state,
                                 boolean checked,
                                 boolean flip) {
      super(renderContext, title);
      myState = state;
      ScreenOrientation orientation = getOrientation(state);
      getTemplatePresentation().setIcon(getOrientationIcon(orientation, flip));
    }

    public void perform() {
      tryUpdateConfiguration();
      updatePresentation();
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setDeviceState(myState);
    }
  }

  private static class SetUiModeAction extends ConfigurationAction {
    @NotNull private final UiMode myUiMode;

    private SetUiModeAction(@NotNull ConfigurationHolder renderContext, @NotNull String title, @NotNull UiMode uiMode, boolean checked) {
      super(renderContext, title);
      myUiMode = uiMode;
      if (checked) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setUiMode(myUiMode);
    }
  }

  private static class SetNightModeAction extends ConfigurationAction {
    @NotNull private final NightMode myNightMode;

    private SetNightModeAction(@NotNull ConfigurationHolder renderContext, @NotNull String title, @NotNull NightMode nightMode, boolean checked) {
      super(renderContext, title);
      myNightMode = nightMode;
      if (checked) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      configuration.setNightMode(myNightMode);
    }
  }
}
