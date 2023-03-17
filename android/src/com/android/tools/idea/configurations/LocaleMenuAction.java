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

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.editors.strings.StringResourceEditorProvider;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.rendering.StudioRenderServiceKt;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import icons.StudioIcons;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class LocaleMenuAction extends DropDownAction {
  private final ConfigurationHolder myRenderContext;

  public LocaleMenuAction(@NotNull ConfigurationHolder renderContext) {
    super("Locale for Preview", "Locale for Preview", null);
    myRenderContext = renderContext;
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

    Configuration conf = myRenderContext.getConfiguration();
    String currentLocalLabel = Locale.getLocaleLabel(conf == null ? Locale.ANY : conf.getLocale(), false);

    List<Locale> locales = getRelevantLocales();

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null && !locales.isEmpty()) {
      String title = Locale.getLocaleLabel(Locale.ANY, false);
      add(new SetLocaleAction(myRenderContext, title, Locale.ANY, currentLocalLabel.equals(title)));
      addSeparator();

      Collections.sort(locales, Locale.LANGUAGE_CODE_COMPARATOR);
      for (Locale locale : locales) {
        title = Locale.getLocaleLabel(locale, false);

        VirtualFile better = ConfigurationMatcher.getBetterMatch(configuration, null, null, locale, null);
        if (better != null) {
          title = ConfigurationAction.getBetterMatchLabel(Locale.getLocaleLabel(locale, true), better, configuration.getFile());
        }

        add(new SetLocaleAction(myRenderContext, title, locale, currentLocalLabel.equals(title)));
      }

      addSeparator();
    }

    add(new EditTranslationAction());

    if (configuration != null && !hasAnyRtlLocales(configuration, locales)) {
      // The switch RtlAction is only added is there are not any RTL locales that you can use to preview the layout
      add(new RtlAction(myRenderContext));
    }

    return true;
  }

  /**
   * Returns whether any of the passed locales is RTL
   */
  private static boolean hasAnyRtlLocales(@NotNull Configuration configuration, @NotNull List<Locale> locales) {
    LayoutLibrary layoutlib = StudioRenderServiceKt.getLayoutLibrary(configuration.getModule(), configuration.getTarget());
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
  private List<Locale> getRelevantLocales() {
    List<Locale> locales = new ArrayList<>();

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return Collections.emptyList();
    }
    ConfigurationModelModule module = configuration.getConfigurationManager().getConfigModule();
    LocaleQualifier specificLocale = configuration.getEditedConfig().getLocaleQualifier();

    // If the layout exists in a non-locale specific folder, then offer all locales, since
    // the user should be able to switch from this layout to some other version. We
    // only lock down this layout to the current locale if the layout only exists for this
    // locale.
    if (specificLocale != null) {
      List<VirtualFile> variations = IdeResourcesUtil.getResourceVariations(configuration.getFile(), false);
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
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
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

    public SetLocaleAction(ConfigurationHolder renderContext, String title, @NotNull Locale locale, boolean isCurrentLocale) {
      // TODO: Rather than passing in the title, update the code to implement update() instead; that
      // way we can lazily compute the label as part of the list rendering
      super(renderContext, title, null);
      myLocale = locale;
      myIsCurrentLocale = isCurrentLocale;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      Toggleable.setSelected(presentation, myIsCurrentLocale);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      super.actionPerformed(event);
      updateActions(event.getDataContext());
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration, boolean commit) {
      if (commit) {
        setProjectWideLocale();
      }
      else {
        // The locale can affect the direction qualifier: don't constrain best match
        // search to the current direction
        configuration.getEditedConfig().setLayoutDirectionQualifier(null);

        configuration.setLocale(myLocale);
      }
    }

    @Override
    protected void pickedBetterMatch(@NotNull VirtualFile file, @NotNull VirtualFile old) {
      super.pickedBetterMatch(file, old);
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        // Save project-wide configuration; not done by regular listening scheme since the previous configuration was not switched
        setProjectWideLocale();
      }
    }

    private void setProjectWideLocale() {
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        // Also set the project-wide locale, since locales (and rendering targets) are project wide
        configuration.getConfigurationManager().setLocale(myLocale);
      }
    }
  }

  private class EditTranslationAction extends AnAction {

    public EditTranslationAction() {
      super("Edit Translations...", null, null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        Module module = configuration.getConfigurationManager().getModule();
        StringResourceEditorProvider.openEditor(module);
      }
    }
  }
}
