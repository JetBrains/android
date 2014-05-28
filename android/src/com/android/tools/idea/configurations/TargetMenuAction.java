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
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
      IAndroidTarget target = configuration.getTarget();
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

    group.add(new TogglePickBestAction(configuration.getConfigurationManager()));
    group.addSeparator();

    IAndroidTarget current = configuration.getTarget();
    IAndroidTarget[] targets = configuration.getConfigurationManager().getTargets();

    boolean haveRecent = false;

    Module module = myRenderContext.getModule();
    int minSdk = -1;
    if (module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        minSdk = facet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel();
      }
    }

    for (int i = targets.length - 1; i >= 0; i--) {
      IAndroidTarget target = targets[i];
      if (!ConfigurationManager.isLayoutLibTarget(target)) {
        continue;
      }

      AndroidVersion version = target.getVersion();
      if (version.getApiLevel() < minSdk) {
        break;
      }
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

    group.addSeparator();
    RenderPreviewMode currentMode = RenderPreviewMode.getCurrent();
    if (currentMode != RenderPreviewMode.API_LEVELS) {
      ConfigurationMenuAction.addApiLevelPreviewAction(myRenderContext, group);
    } else {
      ConfigurationMenuAction.addRemovePreviewsAction(myRenderContext, group);
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

  private static class TogglePickBestAction extends ToggleAction {
    private final ConfigurationManager myManager;

    TogglePickBestAction(ConfigurationManager manager) {
      super("Automatically Pick Best");

      myManager = manager;

      if (manager.getStateManager().getProjectState().isPickTarget()) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myManager.getStateManager().getProjectState().isPickTarget();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myManager.getStateManager().getProjectState().setPickTarget(state);
      if (state) {
        // Make sure we have the best target: force recompute on next getTarget()
        myManager.setTarget(null);
      }
    }
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
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      if (commit) {
        setProjectWideTarget();
      } else {
        configuration.setTarget(myTarget);
      }
    }

    @Override
    protected void pickedBetterMatch(@NotNull VirtualFile file, @NotNull VirtualFile old) {
      super.pickedBetterMatch(file, old);
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        // Save project-wide configuration; not done by regular listening scheme since the previous configuration was not switched
        setProjectWideTarget();
      }
    }

    private void setProjectWideTarget() {
      // Also set the project-wide rendering target, since targets (and locales) are project wide
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        configuration.getConfigurationManager().setTarget(myTarget);
        myRenderContext.requestRender();
      }
    }
  }
}
