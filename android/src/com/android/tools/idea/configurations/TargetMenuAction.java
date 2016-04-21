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

import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.TAG_PREFERENCE_SCREEN;
import static com.android.tools.idea.configurations.Configuration.PREFERENCES_MIN_API;

public class TargetMenuAction extends FlatComboAction {
  // We don't show ancient rendering targets, they're pretty broken
  private static final int SHOW_FROM_API_LEVEL = 7;

  private final ConfigurationHolder myRenderContext;
  private final boolean myUseCompatibilityTarget;

  /**
   * Creates a {@code TargetMenuAction}
   * @param renderContext A {@link ConfigurationHolder} instance
   * @param useCompatibilityTarget when true, this menu action will set a CompatibilityRenderTarget as instead of a real IAndroidTarget
   * @param classicStyle if true, use the pre Android Studio 1.5 configuration toolbar style (temporary compatibility code)
   */
  public TargetMenuAction(ConfigurationHolder renderContext, boolean useCompatibilityTarget, boolean classicStyle) {
    myRenderContext = renderContext;
    myUseCompatibilityTarget = useCompatibilityTarget;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Android version to use when rendering layouts in the IDE");
    presentation.setIcon(classicStyle ? AndroidIcons.Targets : AndroidIcons.NeleIcons.Api);
    updatePresentation(presentation);
  }

  public TargetMenuAction(ConfigurationHolder renderContext, boolean useCompatibilityTarget) {
    this(renderContext, useCompatibilityTarget, !RenderService.NELE_ENABLED);
  }

  public TargetMenuAction(ConfigurationHolder renderContext) {
    this(renderContext, false);
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
          return facet.getAndroidModuleInfo().getMinSdkVersion().getFeatureLevel();
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
      boolean isSelected = target.getVersion().equals(currentTarget.getVersion());
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

      if (!switchToTargetAllowed(configuration, target)) {
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
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);
    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return group;
    }

    group.add(new TogglePickBestAction(configuration.getConfigurationManager()));
    group.addSeparator();

    if (myUseCompatibilityTarget && configuration.getConfigurationManager().getHighestApiTarget() != null) {
      addCompatibilityTargets(group);
    } else {
      addRealTargets(group);
    }

    return group;
  }

  /**
   * Returns if the {@code configuration} allows switching to {@code target}
   */
  private static boolean switchToTargetAllowed(Configuration configuration, IAndroidTarget target) {
    // Return false only if target api level is less than the min api version that supports
    // preferences rendering and this file is a preference file.

    // As an optimization, return early if this is a layout file.
    if (ResourceHelper.getFolderType(configuration.getFile()) != ResourceFolderType.XML) {
      return true;
    }

    PsiFile file = configuration.getPsiFile();
    return file == null ||
           target.getVersion().getFeatureLevel() >= PREFERENCES_MIN_API ||
           !TAG_PREFERENCE_SCREEN.equals(AndroidPsiUtils.getRootTagName(file));
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
          } else {
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
      }
    }
  }
}
