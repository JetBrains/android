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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.ConfigurationSettings;
import com.android.tools.layoutlib.AndroidTargets;
import com.android.tools.module.AndroidModuleInfo;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public class TargetMenuAction extends DropDownAction {
  // We don't show ancient rendering targets, they're pretty broken
  private static final int SHOW_FROM_API_LEVEL = 7;

  private final boolean myUseCompatibilityTarget;

  /**
   * Creates a {@code TargetMenuAction}
   *
   * @param useCompatibilityTarget when true, this menu action will set a CompatibilityRenderTarget as instead of a real IAndroidTarget
   */
  public TargetMenuAction(boolean useCompatibilityTarget) {
    super("API Version for Preview", "API Version for Preview", StudioIcons.LayoutEditor.Toolbar.ANDROID_API);
    myUseCompatibilityTarget = useCompatibilityTarget;
  }

  public TargetMenuAction() {
    this(false);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
    if (configurations == null) {
      return;
    }
    Presentation presentation = e.getPresentation();
    Configuration configuration = Iterables.getFirst(configurations, null);
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

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  /**
   * @return Minimum Sdk Version if defined in the module, otherwise -1
   */
  private int getMinSdkVersion(@NotNull Configuration configuration) {
    AndroidModuleInfo moduleInfo = configuration.getConfigModule().getAndroidModuleInfo();
    if (moduleInfo != null) {
      return moduleInfo.getMinSdkVersion().getFeatureLevel();
    }
    return -1;
  }

  @NotNull
  private List<SetTargetAction> getCompatibilitySetTargetActions(@NotNull Configuration configuration, boolean defaultSelectable) {
    IAndroidTarget highestTarget = configuration.getSettings().getHighestApiTarget();
    assert highestTarget != null;

    List<SetTargetAction> actions = new ArrayList<>();

    int highestApiLevel = highestTarget.getVersion().getFeatureLevel();
    int minApi = Math.max(getMinSdkVersion(configuration), SHOW_FROM_API_LEVEL);
    for (int apiLevel = highestApiLevel; apiLevel >= minApi; apiLevel--) {
      IAndroidTarget target = new CompatibilityRenderTarget(highestTarget, apiLevel, null);
      actions.add(new SetTargetAction(target.getVersionName(), target, defaultSelectable));
    }

    return deduplicateSetTargetAction(actions);
  }

  @NotNull
  private List<SetTargetAction> getRealSetTargetActions(@NotNull Configuration configuration, boolean defaultSelectable) {
    IAndroidTarget[] targets = configuration.getSettings().getTargets();

    List<SetTargetAction> actions = new ArrayList<>();

    boolean haveRecent = false;
    int minSdk = getMinSdkVersion(configuration);

    for (int i = targets.length - 1; i >= 0; i--) {
      IAndroidTarget target = targets[i];
      if (!AndroidTargets.isLayoutLibTarget(target)) {
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

      String title = getRenderingTargetLabel(target, true);
      actions.add(new SetTargetAction(title, target, defaultSelectable));
    }

    return deduplicateSetTargetAction(actions);
  }

  @NotNull
  private List<SetTargetAction> deduplicateSetTargetAction(@NotNull List<SetTargetAction> targetActions) {
    LinkedHashMap<String, SetTargetAction> titleToActionMap = new LinkedHashMap<>();

    for (SetTargetAction targetAction : targetActions) {
      String title = targetAction.myTitle;
      if (titleToActionMap.containsKey(title)) {
        SetTargetAction oldAction = titleToActionMap.get(title);
        int oldRevision = oldAction.myTarget.getRevision();
        int newRevision = targetAction.myTarget.getRevision();
        // Choose the last revision one.
        if (newRevision > oldRevision) {
          titleToActionMap.replace(title, targetAction);
        }
      }
      else {
        titleToActionMap.put(title, targetAction);
      }
    }
    return titleToActionMap.values().stream().toList();
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    removeAll();
    Collection<Configuration> configurations = context.getData(CONFIGURATIONS);
    if (configurations == null) {
      return true;
    }
    Configuration configuration = Iterables.getFirst(configurations, null);
    if (configuration == null) {
      return true;
    }

    ConfigurationSettings settings = configuration.getSettings();
    add(new TogglePickBestAction(settings));
    addSeparator();

    boolean isPickBest = settings.getConfigModule().getConfigurationStateManager().getProjectState().isPickTarget();

    List<SetTargetAction> actions;
    if (myUseCompatibilityTarget && settings.getHighestApiTarget() != null) {
      actions = getCompatibilitySetTargetActions(configuration, isPickBest);
    }
    else {
      actions = getRealSetTargetActions(configuration, isPickBest);
    }
    addAll(actions);

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

    return String.format(Locale.US, "API %1$d: %2$s", version.getApiLevel(), target.getShortClasspathName());
  }

  private static class TogglePickBestAction extends AnAction implements Toggleable {
    private final ConfigurationSettings mySettings;

    TogglePickBestAction(ConfigurationSettings settings) {
      super("Automatically Pick Best");
      mySettings = settings;

      if (settings.getConfigModule().getConfigurationStateManager().getProjectState().isPickTarget()) {
        getTemplatePresentation().putClientProperty(SELECTED_PROPERTY, true);
      }
    }

    private boolean isSelected() {
      return mySettings.getConfigModule().getConfigurationStateManager().getProjectState().isPickTarget();
    }

    private void setSelected(boolean state) {
      mySettings.getConfigModule().getConfigurationStateManager().getProjectState().setPickTarget(state);
      if (state) {
        // Make sure we have the best target: force recompute on next getTarget()
        mySettings.setTarget(null);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      setSelected(!isSelected());
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      Toggleable.setSelected(presentation, isSelected());
    }
  }

  @VisibleForTesting
  static class SetTargetAction extends ConfigurationAction {
    @VisibleForTesting
    final IAndroidTarget myTarget;
    private final String myTitle;
    private final boolean myDefaultSelectable;

    public SetTargetAction(@NotNull final String title,
                           @NotNull final IAndroidTarget target,
                           boolean defaultSelectable) {
      super(title);
      myTarget = target;
      myTitle = title;
      myDefaultSelectable = defaultSelectable;
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      if (commit) {
        setProjectWideTarget(configuration);
      }
      else {
        configuration.setTarget(myTarget);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
      if (configurations == null) {
        return;
      }
      Configuration config = Iterables.getFirst(configurations, null);
      if (config != null) {
        config.getSettings().getConfigModule().getConfigurationStateManager().getProjectState().setPickTarget(false);
      }
      Toggleable.setSelected(e.getPresentation(), true);
      super.actionPerformed(e);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
      if (configurations == null) {
        return;
      }
      Configuration config = Iterables.getFirst(configurations, null);
      if (config == null) {
        return;
      }
      IAndroidTarget current = config.getTarget();
      boolean selected = !myDefaultSelectable && current != null && myTarget.getVersion().equals(current.getVersion());
      Toggleable.setSelected(e.getPresentation(), selected);
    }

    @Override
    protected void pickedBetterMatch(@NotNull Configuration configuration, @NotNull VirtualFile file, @NotNull VirtualFile old) {
      super.pickedBetterMatch(configuration, file, old);
      // Save project-wide configuration; not done by regular listening scheme since the previous configuration was not switched
      setProjectWideTarget(configuration);
    }

    private void setProjectWideTarget(@NotNull Configuration configuration) {
      // Also set the project-wide rendering target, since targets (and locales) are project wide
      configuration.getSettings().setTarget(myTarget);
    }
  }
}
