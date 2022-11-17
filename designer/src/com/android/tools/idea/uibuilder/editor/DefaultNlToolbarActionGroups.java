/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.actions.SetColorBlindModeAction;
import com.android.tools.idea.actions.SetScreenViewProviderAction;
import com.android.tools.idea.common.actions.IssueNotificationAction;
import com.android.tools.idea.common.actions.NextDeviceAction;
import com.android.tools.idea.common.actions.ToggleDeviceNightModeAction;
import com.android.tools.idea.common.actions.ToggleDeviceOrientationAction;
import com.android.tools.idea.common.editor.ToolbarActionGroups;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.android.tools.idea.configurations.DeviceMenuAction2;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.configurations.NightModeMenuAction;
import com.android.tools.idea.configurations.SystemUiModeAction;
import com.android.tools.idea.configurations.OrientationMenuAction;
import com.android.tools.idea.configurations.TargetMenuAction;
import com.android.tools.idea.configurations.ThemeMenuAction;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.common.actions.RefreshRenderAction;
import com.android.tools.idea.ui.designer.overlays.OverlayConfiguration;
import com.android.tools.idea.ui.designer.overlays.OverlayMenuAction;
import com.android.tools.idea.uibuilder.actions.LayoutEditorHelpAssistantAction;
import com.android.tools.idea.uibuilder.actions.SwitchToNextScreenViewProviderAction;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Permanent toolbar for the {@link NlDesignSurface}. This toolbar and its contained object
 * life cycles should match the {@link DesignSurface} one.
 */
public final class DefaultNlToolbarActionGroups extends ToolbarActionGroups {

  public DefaultNlToolbarActionGroups(@NotNull NlDesignSurface surface) {
    super(surface);
  }

  @NotNull
  @Override
  protected ActionGroup getEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(LayoutEditorHelpAssistantAction.BUNDLE_ID));
    return group;
  }

  @NotNull
  @Override
  protected ActionGroup getNorthGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    if (isInVisualizationTool()) {
      // There is no north group in visualization for now.
      return group;
    }

    List<NlModel> models = mySurface.getModels();
    VirtualFile file;
    if (!models.isEmpty()) {
      file = models.get(0).getVirtualFile();
    }
    else {
      file = null;
    }
    LayoutQualifierDropdownMenu dropdown = new LayoutQualifierDropdownMenu(file);
    group.add(dropdown);
    group.addSeparator();

    DropDownAction designModeAction = createDesignModeAction();
    appendShortcutText(designModeAction, SwitchToNextScreenViewProviderAction.getInstance());
    group.add(designModeAction);
    group.addSeparator();

    OrientationMenuAction orientationMenuAction = new OrientationMenuAction(mySurface::getConfiguration);
    appendShortcutText(orientationMenuAction, ToggleDeviceOrientationAction.getInstance());
    group.add(orientationMenuAction);

    if (StudioFlags.NELE_OVERLAY_PROVIDER.get()
        && OverlayConfiguration.EP_NAME.hasAnyExtensions()) {
      group.addSeparator();
      OverlayMenuAction overlayAction = new OverlayMenuAction(mySurface);
      group.add(overlayAction);
    }

    group.addSeparator();
    if (StudioFlags.NELE_DYNAMIC_THEMING_ACTION.get()) {
      SystemUiModeAction systemUiModeAction = new SystemUiModeAction(mySurface::getConfiguration);
      appendShortcutText(systemUiModeAction, ToggleDeviceNightModeAction.getInstance());
      group.add(systemUiModeAction);
    }
    else {
      NightModeMenuAction nightModeAction = new NightModeMenuAction(mySurface::getConfiguration);
      appendShortcutText(nightModeAction, ToggleDeviceNightModeAction.getInstance());
      group.add(nightModeAction);
    }

    group.addSeparator();
    if (StudioFlags.NELE_NEW_DEVICE_MENU.get()) {
      DeviceMenuAction2 menuAction2 = new DeviceMenuAction2(() -> mySurface.getConfigurations().stream().findFirst().orElse(null),
                                                            (oldDevice, newDevice) -> mySurface.zoomToFit());
      appendShortcutText(menuAction2, NextDeviceAction.getInstance());
      group.add(menuAction2);
    }
    else {
      DeviceMenuAction menuAction = new DeviceMenuAction(mySurface::getConfiguration, (oldDevice, newDevice) -> mySurface.zoomToFit());
      appendShortcutText(menuAction, NextDeviceAction.getInstance());
      group.add(menuAction);
    }

    group.add(new TargetMenuAction(mySurface::getConfiguration));
    group.add(new ThemeMenuAction(mySurface::getConfiguration));

    group.addSeparator();
    group.add(new LocaleMenuAction(mySurface::getConfiguration));

    group.addSeparator();
    return group;
  }

  private void appendShortcutText(@NotNull AnAction targetAction , @NotNull AnAction action) {
    String shortcutsText = KeymapUtil.getPreferredShortcutText(action.getShortcutSet().getShortcuts());
    Presentation presentation = targetAction.getTemplatePresentation();
    if (!shortcutsText.isEmpty()) {
      presentation.setDescription(String.format("%s (%s)", presentation.getDescription(), shortcutsText));
    }
  }

  @NotNull
  private DropDownAction createDesignModeAction() {
    NlDesignSurface nlDesignSurface = (NlDesignSurface) mySurface;
    DropDownAction designSurfaceMenu =
      new DropDownAction("Select Design Surface", "Select Design Surface", StudioIcons.LayoutEditor.Toolbar.VIEW_MODE);
    designSurfaceMenu.addAction(new SetScreenViewProviderAction(NlScreenViewProvider.RENDER, nlDesignSurface));
    designSurfaceMenu.addAction(new SetScreenViewProviderAction(NlScreenViewProvider.BLUEPRINT, nlDesignSurface));
    designSurfaceMenu.addAction(new SetScreenViewProviderAction(NlScreenViewProvider.RENDER_AND_BLUEPRINT, nlDesignSurface));

    DefaultActionGroup colorBlindMode = DefaultActionGroup.createPopupGroup(() -> "Color Blind Modes");
    colorBlindMode.addAction(new SetColorBlindModeAction(ColorBlindMode.PROTANOPES, nlDesignSurface));
    colorBlindMode.addAction(new SetColorBlindModeAction(ColorBlindMode.PROTANOMALY, nlDesignSurface));
    colorBlindMode.addAction(new SetColorBlindModeAction(ColorBlindMode.DEUTERANOPES, nlDesignSurface));
    colorBlindMode.addAction(new SetColorBlindModeAction(ColorBlindMode.DEUTERANOMALY, nlDesignSurface));
    colorBlindMode.addAction(new SetColorBlindModeAction(ColorBlindMode.TRITANOPES, nlDesignSurface));
    designSurfaceMenu.addAction(colorBlindMode);

    designSurfaceMenu.addSeparator();
    // Get the action instead of creating a new one, to make the popup menu display the shortcut.
    designSurfaceMenu.addAction(RefreshRenderAction.getInstance());
    return designSurfaceMenu;
  }

  @NotNull
  @Override
  protected ActionGroup getNorthEastGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    if (isInVisualizationTool()) {
      // Ignore Issue panel in visualisation.
      return group;
    }
    group.add(IssueNotificationAction.getInstance());
    return group;
  }

  private boolean isInVisualizationTool() {
    return ((NlDesignSurface)mySurface).getScreenViewProvider() == NlScreenViewProvider.VISUALIZATION;
  }
}
