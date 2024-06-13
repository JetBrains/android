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

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.ConfigurationModelModule;
import com.android.tools.idea.configurations.ConfigurationFileUtil;
import com.android.tools.idea.configurations.ConfigurationMatcher;
import com.android.tools.idea.configurations.StudioConfigurationModelModule;
import com.android.tools.idea.editors.strings.StringResourceEditorProvider;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.StudioRenderServiceKt;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.res.ResourceRepositoryManager;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocaleMenuAction extends DropDownAction {
  public LocaleMenuAction() {
    super("Locale for Preview", "Locale for Preview", null);
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  @Override
  protected boolean updateActions(@NotNull DataContext context) {
    removeAll();
    // TODO: Offer submenus, lazily populated, which offer languages either by code or by name.
    // However, this doesn't currently work for the JBPopup dialog we're using as part
    // of the combo action (and using the JBPopup dialog rather than a Swing menu has some
    // other advantages: fitting in with the overall IDE look and feel (e.g. dark colors),
    // allowing typing to filter, etc.

    Collection<Configuration> configurations = context.getData(CONFIGURATIONS);
    if (configurations == null) {
      return true;
    }
    Configuration configuration = Iterables.getFirst(configurations, null);
    String currentLocalLabel = Locale.getLocaleLabel(configuration == null ? Locale.ANY : configuration.getLocale(), false);

    List<Locale> locales = getRelevantLocales(configuration);

    if (configuration != null && !locales.isEmpty()) {
      String title = Locale.getLocaleLabel(Locale.ANY, false);
      add(new SetLocaleAction(title, Locale.ANY, currentLocalLabel.equals(title)));
      addSeparator();

      locales.sort(Locale.LANGUAGE_CODE_COMPARATOR);
      for (Locale locale : locales) {
        title = Locale.getLocaleLabel(locale, false);

        VirtualFile better = ConfigurationMatcher.getBetterMatch(configuration, null, null, locale, null);
        if (better != null) {
          VirtualFile file = ConfigurationFileUtil.getVirtualFile(configuration);
          title = ConfigurationAction.getBetterMatchLabel(Locale.getLocaleLabel(locale, true), better, file);
        }

        add(new SetLocaleAction(title, locale, currentLocalLabel.equals(title)));
      }

      addSeparator();
    }

    add(new EditTranslationAction());

    if (configuration != null && !hasAnyRtlLocales(configuration, locales)) {
      // The switch RtlAction is only added is there are not any RTL locales that you can use to preview the layout
      add(new RtlAction());
    }

    return true;
  }

  /**
   * Returns whether any of the passed locales is RTL
   */
  private static boolean hasAnyRtlLocales(@NotNull Configuration configuration, @NotNull List<Locale> locales) {
    Module module = ((StudioConfigurationModelModule)(configuration.getConfigModule())).getModule();
    LayoutLibrary layoutlib = StudioRenderServiceKt.getLayoutLibrary(module, configuration.getTarget());
    if (layoutlib == null) {
      return false;
    }

    return locales.stream().anyMatch(locale -> layoutlib.isRtl(locale.toLocaleId()));
  }

  /**
   * Like {@link StudioResourceRepositoryManager#getLocalesInProject} but filters out locales not compatible
   * with language and region qualifiers in the current configuration's folder config.
   *
   * @return the list of relevant locales in the project
   */
  @NotNull
  private List<Locale> getRelevantLocales(@Nullable Configuration configuration) {
    List<Locale> locales = new ArrayList<>();

    if (configuration == null) {
      return Collections.emptyList();
    }
    ConfigurationModelModule module = configuration.getConfigModule();
    LocaleQualifier specificLocale = configuration.getEditedConfig().getLocaleQualifier();

    // If the layout exists in a non-locale specific folder, then offer all locales, since
    // the user should be able to switch from this layout to some other version. We
    // only lock down this layout to the current locale if the layout only exists for this
    // locale.
    if (specificLocale != null) {
      VirtualFile file = ConfigurationFileUtil.getVirtualFile(configuration);
      List<VirtualFile> variations = IdeResourcesUtil.getResourceVariations(file, false);
      for (VirtualFile variation : variations) {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(variation.getParent().getName());
        if (config != null && config.getLocaleQualifier() == null) {
          specificLocale = null;
          break;
        }
      }
    }

    ResourceRepositoryManager repoManager = module.getResourceRepositoryManager();
    Set<LocaleQualifier> languages =
      repoManager != null ? ResourceRepositoryUtil.getLocales(repoManager.getProjectResources()) : Collections.emptySet();
    for (LocaleQualifier l : languages) {
      if (specificLocale != null && !specificLocale.isMatchFor(l)) {
        continue;
      }
      locales.add(Locale.create(l));
    }

    return locales;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    updatePresentation(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private void updatePresentation(@NotNull AnActionEvent e) {
    Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
    if (configurations == null) {
      return;
    }
    Configuration configuration = Iterables.getFirst(configurations, null);
    Presentation presentation = e.getPresentation();
    boolean visible = configuration != null;
    if (visible) {
      // TEMPORARY WORKAROUND:
      // We don't properly sync the project locale to layouts yet, so in the meantime
      // show the actual locale being used rather than the intended locale, so as not
      // to be totally confusing:
      // Locale locale = configuration.isLocaleSpecificLayout()
      //                ? configuration.getLocale() : configuration.getConfigurationManager().getLocale();
      Locale locale = configuration.getLocale();
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.LANGUAGE);
      String brief = Locale.getLocaleLabel(locale, true);
      presentation.setText(brief);
    }
    else {
      presentation.setIcon(StudioIcons.LayoutEditor.Toolbar.LANGUAGE);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  private class SetLocaleAction extends ConfigurationAction {
    private final Locale myLocale;
    private final boolean myIsCurrentLocale;

    public SetLocaleAction(String title, @NotNull Locale locale, boolean isCurrentLocale) {
      // TODO: Rather than passing in the title, update the code to implement update() instead; that
      // way we can lazily compute the label as part of the list rendering
      super(title, null);
      myLocale = locale;
      myIsCurrentLocale = isCurrentLocale;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      Toggleable.setSelected(presentation, myIsCurrentLocale);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      super.actionPerformed(event);
      updateActions(event.getDataContext());
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      if (commit) {
        setProjectWideLocale(configuration);
      }
      else {
        // The locale can affect the direction qualifier: don't constrain best match
        // search to the current direction
        configuration.getEditedConfig().setLayoutDirectionQualifier(null);

        configuration.setLocale(myLocale);
      }
    }

    @Override
    protected void pickedBetterMatch(@NotNull Configuration configuration, @NotNull VirtualFile file, @NotNull VirtualFile old) {
      super.pickedBetterMatch(configuration, file, old);
      // Save project-wide configuration; not done by regular listening scheme since the previous configuration was not switched
      setProjectWideLocale(configuration);
    }

    private void setProjectWideLocale(@NotNull Configuration configuration) {
      // Also set the project-wide locale, since locales (and rendering targets) are project wide
      configuration.getSettings().setLocale(myLocale);
    }
  }

  private static class EditTranslationAction extends AnAction {

    public EditTranslationAction() {
      super("Edit Translations...", null, null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Collection<Configuration> configurations = e.getData(CONFIGURATIONS);
      if (configurations == null) {
        return;
      }
      Configuration configuration = Iterables.getFirst(configurations, null);
      if (configuration != null) {
        Module module = ((StudioConfigurationModelModule)(configuration.getConfigModule())).getModule();
        StringResourceEditorProvider.openEditor(module);
      }
    }
  }
}
