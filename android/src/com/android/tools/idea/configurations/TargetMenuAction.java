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
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TargetMenuAction extends DropDownAction {
  // We don't show ancient rendering targets, they're pretty broken
  private static final int SHOW_FROM_API_LEVEL = 7;

  private final ConfigurationHolder myRenderContext;
  private final boolean myUseCompatibilityTarget;

  /**
   * Creates a {@code TargetMenuAction}
   *
   * @param renderContext          A {@link ConfigurationHolder} instance
   * @param useCompatibilityTarget when true, this menu action will set a CompatibilityRenderTarget as instead of a real IAndroidTarget
   */
  public TargetMenuAction(ConfigurationHolder renderContext, boolean useCompatibilityTarget) {
    super("", "API Version in Editor", StudioIcons.LayoutEditor.Toolbar.ANDROID_API);
    myRenderContext = renderContext;
    myUseCompatibilityTarget = useCompatibilityTarget;
    Presentation presentation = getTemplatePresentation();
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

  public TargetMenuAction(ConfigurationHolder renderContext) {
    this(renderContext, false);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      IAndroidTarget target = configuration.getTarget();
      String brief = getRenderingTargetLabel(target, true);
      if (!brief.equals(presentation.getText())) {
        presentation.setText(brief, false);
      }
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  /**
   * @return Minimum Sdk Version if defined in the module, otherwise -1
   */
  private int getMinSdkVersion() {
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      Module module = configuration.getModule();
      if (module != null) {
        AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet != null) {
          return AndroidModuleInfo.getInstance(facet).getMinSdkVersion().getFeatureLevel();
        }
      }
    }
    return -1;
  }

  private void addCompatibilityTargets(@NotNull DefaultActionGroup group) {
    Configuration configuration = myRenderContext.getConfiguration();
    assert configuration != null;

    IAndroidTarget currentTarget = configuration.getTarget();
    IAndroidTarget highestTarget = configuration.getConfigurationManager().getHighestApiTarget();
    assert highestTarget != null;

    int highestApiLevel = highestTarget.getVersion().getFeatureLevel();
    int minApi = Math.max(getMinSdkVersion(), SHOW_FROM_API_LEVEL);
    for (int apiLevel = highestApiLevel; apiLevel >= minApi; apiLevel--) {
      IAndroidTarget target = new CompatibilityRenderTarget(highestTarget, apiLevel, null);
      boolean isSelected = currentTarget != null && target.getVersion().equals(currentTarget.getVersion());
      group.add(new SetTargetAction(myRenderContext, target.getVersionName(), target, isSelected));
    }
  }

  private void addRealTargets(@NotNull DefaultActionGroup group) {

    Configuration configuration = myRenderContext.getConfiguration();
    assert configuration != null;

    IAndroidTarget current = configuration.getTarget();
    IAndroidTarget[] targets = configuration.getConfigurationManager().getTargets();

    boolean haveRecent = false;
    int minSdk = getMinSdkVersion();

    for (int i = targets.length - 1; i >= 0; i--) {
      IAndroidTarget target = targets[i];
      if (!ConfigurationManager.isLayoutLibTarget(target)) {
        continue;
      }

      AndroidVersion version = target.getVersion();
      if (version.getFeatureLevel() < minSdk) {
        continue;
      }
      if (version.getApiLevel() >= SHOW_FROM_API_LEVEL) {
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
  }

  @Override
  protected boolean updateActions() {
    removeAll();
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return true;
    }

    add(new TogglePickBestAction(configuration.getConfigurationManager()));
    addSeparator();

    if (myUseCompatibilityTarget && configuration.getConfigurationManager().getHighestApiTarget() != null) {
      addCompatibilityTargets(this);
    }
    else {
      addRealTargets(this);
    }

    return true;
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
        String codename = version.getCodename();
        if (codename != null && !codename.isEmpty()) {
          if (codename.equals("MNC")) {
            return "M";
          }
          // The target menu brief label is deliberately short; typically it's just a 2 digit
          // API number. If this is a preview platform we should display the codename, but only
          // if it's a really short codename; if not, just display the first letter (since Android
          // platforms are typically identified by a letter anyway).
          if (codename.length() <= 3) {
            return codename;
          }
          else {
            return Character.toString(codename.charAt(0));
          }
        }
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

    public SetTargetAction(@NotNull ConfigurationHolder renderContext, @NotNull final String title,
                           @NotNull final IAndroidTarget target, final boolean select) {
      super(renderContext, title, select ? AllIcons.Actions.Checked : null);
      myTarget = target;
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      if (commit) {
        setProjectWideTarget();
      }
      else {
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
      }
    }
  }
}
