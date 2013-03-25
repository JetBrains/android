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
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class OrientationMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public OrientationMenuAction(RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Go to next state");
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      State current = configuration.getDeviceState();
      if (current != null) {
        State flip = configuration.getNextDeviceState(current);
        if (flip != null) {
          ScreenOrientation orientation = getOrientation(flip);
          presentation.setIcon(getOrientationIcon(orientation, true));
        }
      }
    }
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
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      Device device = configuration.getDevice();
      State current = configuration.getDeviceState();
      if (device != null) {
        List<State> states = device.getAllStates();

        if (states.size() > 1 && current != null) {
          State flip = configuration.getNextDeviceState(current);
          String flipName = flip != null ? flip.getName() : current.getName();
          String title = String.format("Switch to %1$s", flipName);
          group.add(new SetDeviceStateAction(myRenderContext, title, flip == null ? current : flip, false, true));
          group.addSeparator();
        }

        for (State config : states) {
          group.add(new SetDeviceStateAction(myRenderContext, config.getName(), config, config == current, false));
        }
        group.addSeparator();
      }

      // TODO: UI MODE
      // TODO: DOCK MODE
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

    private SetDeviceStateAction(@NotNull RenderContext renderContext, @NotNull String title, @NotNull State state,
                                 boolean checked, boolean flip) {
      super(renderContext, title);
      myState = state;
      ScreenOrientation orientation = getOrientation(state);
      getTemplatePresentation().setIcon(getOrientationIcon(orientation, flip));
    }

    public void perform() {
      tryUpdateConfiguration();
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration) {
      configuration.setDeviceState(myState);
    }
  }
}
