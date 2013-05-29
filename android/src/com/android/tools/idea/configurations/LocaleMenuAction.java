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

import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.ide.common.resources.configuration.RegionQualifier;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.ProjectResources;
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class LocaleMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public LocaleMenuAction(RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Locale to render layout with in the IDE");
    updatePresentation(presentation);
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    // TODO: Offer submenus, lazily populated, which offer languages either by code or by name.
    // However, this doesn't currently work for the JBPopup dialog we're using as part
    // of the combo action (and using the JBPopup dialog rather than a Swing menu has some
    // other advantages: fitting in with the overall IDE look and feel (e.g. dark colors),
    // allowing typing to filter, etc.

    List<Locale> locales = getRelevantLocales();
    if (locales.size() > 0) {
      group.add(new SetLocaleAction(myRenderContext, getLocaleLabel(Locale.ANY, false), Locale.ANY));
      group.addSeparator();

      Collections.sort(locales, Locale.LANGUAGE_CODE_COMPARATOR);
      for (Locale locale : locales) {
        String title = getLocaleLabel(locale, false);
        group.add(new SetLocaleAction(myRenderContext, title, locale));
      }

      group.addSeparator();
    }

    group.add(new AddTranslationAction());

    if (locales.size() > 1) {
      group.addSeparator();
      if (RenderPreviewMode.getCurrent() != RenderPreviewMode.LOCALES) {
        ConfigurationMenuAction.addLocalePreviewAction(myRenderContext, group, true);
      } else {
        ConfigurationMenuAction.addRemovePreviewsAction(myRenderContext, group);
      }
    }

    return group;
  }

  /**
   * Like {@link ConfigurationManager#getLocales} but filters out locales not compatible
   * with language and region qualifiers in the current configuration's folder config
   *
   * @return the list of relevant locales in the project
   */
  @NotNull
  private List<Locale> getRelevantLocales() {
    List<Locale> locales = new ArrayList<Locale>();

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration == null) {
      return Collections.emptyList();
    }
    Module module = configuration.getConfigurationManager().getModule();
    ProjectResources projectResources = ProjectResources.get(module, true);
    SortedSet<String> languages = projectResources.getLanguages();

    LanguageQualifier specificLanguage = configuration.getEditedConfig().getLanguageQualifier();
    RegionQualifier specificRegion = configuration.getEditedConfig().getRegionQualifier();

    // If the layout exists in a non-locale specific folder, then offer all locales, since
    // the user should be able to switch from this layout to some other version. We
    // only lock down this layout to the current locale if the layout only exists for this
    // locale.
    if (specificLanguage != null || specificRegion != null) {
      List<VirtualFile> variations = ResourceHelper.getResourceVariations(configuration.getFile(), false);
      for (VirtualFile variation : variations) {
        FolderConfiguration config = FolderConfiguration.getConfigForFolder(variation.getParent().getName());
        if (config != null && config.getLanguageQualifier() == null) {
          specificLanguage = null;
          specificRegion = null;
          break;
        }
      }
    }

    for (String language : languages) {
      if (specificLanguage != null && !language.equals(specificLanguage.getValue())) {
        continue;
      }

      LanguageQualifier languageQualifier = new LanguageQualifier(language);
      locales.add(Locale.create(languageQualifier));

      SortedSet<String> regions = projectResources.getRegions(language);
      for (String region : regions) {
        if (specificRegion != null && !region.equals(specificRegion.getValue())) {
          continue;
        }

        locales.add(Locale.create(languageQualifier, new RegionQualifier(region)));
      }
    }

    return locales;
  }

  @NotNull
  private static List<Locale> getAllLocales() {
    Set<String> languageCodes = LocaleManager.getLanguageCodes();
    List<String> sorted = new ArrayList<String>(languageCodes);
    Collections.sort(sorted);
    List<Locale> locales = new ArrayList<Locale>(languageCodes.size());
    for (String language : languageCodes) {
      Locale locale = Locale.create(new LanguageQualifier(language));
      locales.add(locale);
    }
    return locales;
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
      // TEMPORARY WORKAROUND:
      // We don't properly sync the project locale to layouts yet, so in the mean time
      // show the actual locale being used rather than the intended locale, so as not
      // to be totally confusing:
      //Locale locale = configuration.isLocaleSpecificLayout()
      //                ? configuration.getLocale() : configuration.getConfigurationManager().getLocale();
      Locale locale = configuration.getLocale();
      if (locale == Locale.ANY) {
        presentation.setIcon(AndroidIcons.Globe);
      } else {
        presentation.setIcon(locale.getFlagImage());
      }
      String brief = getLocaleLabel(locale, true);
      presentation.setText(brief);
    } else {
      presentation.setIcon(AndroidIcons.Globe);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  @Override
  protected int getMaxRows() {
    return 10;
  }

  /**
   * Returns a suitable label to use to display the given locale
   *
   * @param locale the locale to look up a label for
   * @param brief  if true, generate a brief label (suitable for a toolbar
   *               button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  @NotNull
  public static String getLocaleLabel(@Nullable Locale locale, boolean brief) {
    if (locale == null) {
      return "";
    }

    if (!locale.hasLanguage()) {
      if (brief) {
        // Just use the icon
        return "";
      }

      return "Default";
    }

    String languageCode = locale.language.getValue();
    String languageName = LocaleManager.getLanguageName(languageCode);

    if (!locale.hasRegion()) {
      // TODO: Make the region string use "Other" instead of "Any" if
      // there is more than one region for a given language
      //if (regions.size() > 0) {
      //    return String.format("%1$s / Other", language);
      //} else {
      //    return String.format("%1$s / Any", language);
      //}
      if (!brief && languageName != null) {
        return String.format("%1$s (%2$s)", languageName, languageCode);
      }
      else {
        return languageCode;
      }
    }
    else {
      String regionCode = locale.region.getValue();
      if (!brief && languageName != null) {
        String regionName = LocaleManager.getRegionName(regionCode);
        if (regionName != null) {
          return String.format("%1$s (%2$s) in %3$s (%4$s)", languageName, languageCode, regionName, regionCode);
        }
        return String.format("%1$s (%2$s) in %3$s", languageName, languageCode, regionCode);
      }
      return String.format("%1$s / %2$s", languageCode, regionCode);
    }
  }

  private static class SetLocaleAction extends ConfigurationAction {
    private final Locale myLocale;

    public SetLocaleAction(RenderContext renderContext, String title, @NotNull Locale locale) {
      // TODO: Rather than passing in the title, update the code to implement update() instead; that
      // way we can lazily compute the label as part of the list rendering
      super(renderContext, title, locale.getFlagImage());
      myLocale = locale;
    }

    @Override
    protected void updateConfiguration(@NotNull Configuration configuration) {
      if (configuration == myRenderContext.getConfiguration()) {
        setProjectWideLocale();
      }
      configuration.setLocale(myLocale);
    }

    @Override
    protected void pickedBetterMatch(@NotNull VirtualFile file) {
      super.pickedBetterMatch(file);
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        // Save project-wide configuration; not done by regular listening scheme since the previous configuration was not switched
        setProjectWideLocale();
        Module module = myRenderContext.getModule();
        if (module != null) {
          ConfigurationStateManager stateManager = ConfigurationStateManager.get(module.getProject());
          ConfigurationProjectState projectState = stateManager.getProjectState();
          projectState.saveState(configuration);
        }
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

  private class AddTranslationAction extends AnAction {

    public AddTranslationAction() {
      super("Add Translation...", null, AndroidIcons.Globe);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      DataContext context = e.getDataContext();

      // List known locales, except those already present
      List<Locale> locales = getAllLocales();
      locales.removeAll(getRelevantLocales());

      Collections.sort(locales, Locale.LANGUAGE_NAME_COMPARATOR);
      DefaultActionGroup group = new DefaultActionGroup(null, true);
      for (Locale locale : locales) {
        String title = getLocaleLabel(locale, false);
        group.add(new CreateLocaleAction(title, locale));
      }

      JBPopupFactory factory = JBPopupFactory.getInstance();
      ListPopup popup = factory.createActionGroupPopup("Select language to create (type to filter)", group, context,
                                                       JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true, null/*onDispose*/, 10);
      popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
      JComponent content = popup.getContent();
      Dimension preferredSize = content.getPreferredSize();
      if (preferredSize.height > 300) {
        preferredSize.height = 300;
        content.setPreferredSize(preferredSize);
      }
      RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(context);
      popup.show(relativePoint);

      // To use a popup menu instead:
      //ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, group);
    }
  }

  private class CreateLocaleAction extends AnAction {
    private final Locale myLocale;

    public CreateLocaleAction(String title, @NotNull Locale locale) {
      super(title, null, locale.getFlagImage());
      myLocale = locale;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Module module = myRenderContext.getModule();
      if (module == null) {
        return;
      }
      ProjectResources projectResources = ProjectResources.get(module, true);
      TranslationDialog dialog = new TranslationDialog(module, projectResources, myLocale);
      dialog.show();
      if (dialog.isOK()) {
        if (dialog.createTranslation()) {
          // Switch to the newly created translation. Reuse the SetLocaleAction
          // such that we don't just set the locale on the configuration, but project-wide
          // as well .
          new SetLocaleAction(myRenderContext, "", myLocale).actionPerformed(e);
        }
        RenderPreviewManager.bumpRevision();
      }
    }
  }
}
