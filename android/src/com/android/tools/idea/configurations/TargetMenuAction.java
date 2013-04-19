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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TargetMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public TargetMenuAction(RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Android version to use when rendering layouts in the IDE");
    presentation.setIcon(AndroidIcons.Targets);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      IAndroidTarget target = configuration.isTargetSpecificLayout()
                      ? configuration.getTarget() : configuration.getConfigurationManager().getTarget();
      String brief = getRenderingTargetLabel(target, true);
      presentation.setText(brief);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return group;
    }
    IAndroidTarget current = configuration.getTarget();
    List<IAndroidTarget> targets = configuration.getConfigurationManager().getTargets();

    boolean haveRecent = false;

    for (int i = targets.size() - 1; i >= 0; i--) {
      IAndroidTarget target = targets.get(i);

      AndroidVersion version = target.getVersion();
      if (version.getApiLevel() >= 7) {
        haveRecent = true;
      }
      else if (haveRecent) {
        // Don't show ancient rendering targets; they're pretty broken
        // (unless of course all you have are ancient targets)
        break;
      }

      String title = getRenderingTargetLabel(target, false);
      boolean select = current == target;
      group.add(new SetTargetAction(myRenderContext, title, target, select));
    }

    return group;
  }

  /**
   * Returns a suitable label to use to display the given rendering target
   *
   * @param target the target to produce a label for
   * @param brief  if true, generate a brief label (suitable for a toolbar
   *               button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  public static String getRenderingTargetLabel(@Nullable IAndroidTarget target, boolean brief) {
    if (target == null) {
      return "<null>";
    }

    AndroidVersion version = target.getVersion();

    if (brief) {
      if (target.isPlatform()) {
        return Integer.toString(version.getApiLevel());
      }
      else {
        return target.getName() + ':' + Integer.toString(version.getApiLevel());
      }
    }

    return String.format("API %1$d: %2$s", version.getApiLevel(), target.getShortClasspathName());
  }

  private static class SetTargetAction extends ConfigurationAction {
    private final IAndroidTarget myTarget;

    public SetTargetAction(@NotNull RenderContext renderContext, @NotNull final String title, @NotNull final IAndroidTarget target,
                           final boolean select) {
      super(renderContext, title);
      myTarget = target;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration) {
      if (configuration == myRenderContext.getConfiguration()) {
        setProjectWideTarget();
      }
      configuration.setTarget(myTarget);
    }

    @Override
    protected void pickedBetterMatch(@NotNull VirtualFile file) {
      super.pickedBetterMatch(file);
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        // Save project-wide configuration; not done by regular listening scheme since the previous configuration was not switched
        setProjectWideTarget();
        ConfigurationStateManager stateManager = ConfigurationStateManager.get(myRenderContext.getModule().getProject());
        ConfigurationProjectState projectState = stateManager.getProjectState();
        projectState.saveState(configuration);
      }
    }

    private void setProjectWideTarget() {
      // Also set the project-wide rendering target, since targets (and locales) are project wide
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        configuration.getConfigurationManager().setTarget(myTarget);
      }
    }
  }
}
