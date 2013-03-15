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

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ScreenSize;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.State;
import com.android.tools.idea.rendering.ResourceHelper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import icons.AndroidIcons;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ThemeData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class ThemeMenuAction extends FlatComboAction {
  private final ConfigurationToolBar myConfigurationToolBar;

  public ThemeMenuAction(@NotNull ConfigurationToolBar configurationToolBar) {
    myConfigurationToolBar = configurationToolBar;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Theme to render layout with");
    presentation.setIcon(AndroidIcons.Themes);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myConfigurationToolBar.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String brief = getThemeLabel(configuration.getTheme(), true);
      presentation.setText(brief);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  /**
   * Returns a suitable label to use to display the given theme
   *
   * @param theme the theme to produce a label for
   * @param brief if true, generate a brief label (suitable for a toolbar
   *            button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  @NotNull
  public static String getThemeLabel(@Nullable String theme, boolean brief) {
    if (theme == null) {
      return "";
    }
    theme = ResourceHelper.styleToTheme(theme);

    if (brief) {
      int index = theme.lastIndexOf('.');
      if (index < theme.length() - 1) {
        return theme.substring(index + 1);
      }
    }
    return theme;
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup(null, true);

    Configuration configuration = myConfigurationToolBar.getConfiguration();
    if (configuration != null) {
      String current = configuration.getTheme();
      ConfigurationManager manager = configuration.getConfigurationManager();
      // TODO: Preferred theme
      // TODO: Manifest themes
      // TODO: Split up by theme category (light, dark, etc)
      List<String> projectThemes = manager.getProjectThemes();
      List<String> frameworkThemes = manager.getFrameworkThemes(configuration.getTarget());
      if (!projectThemes.isEmpty()) {
        for (String theme : projectThemes) {
          group.add(new SetThemeAction(theme, theme.equals(current)));
        }
        if (!frameworkThemes.isEmpty()) {
          group.addSeparator();
        }
      }
      if (!frameworkThemes.isEmpty()) {
        for (String theme : frameworkThemes) {
          group.add(new SetThemeAction(theme, theme.equals(current)));
        }
      }
    }

    return group;
  }

  private static void doCollectFrameworkThemes(AndroidFacet facet,
                                               @NotNull AndroidTargetData targetData,
                                               List<ThemeData> themes,
                                               Set<ThemeData> addedThemes) {
    final List<String> frameworkThemeNames = new ArrayList<String>(targetData.getThemes(facet));
    Collections.sort(frameworkThemeNames);
    for (String themeName : frameworkThemeNames) {
      final ThemeData themeData = new ThemeData(themeName, false);
      if (addedThemes.add(themeData)) {
        themes.add(themeData);
      }
    }
  }

  private void collectThemesFromManifest(final AndroidFacet facet,
                                         final List<ThemeData> resultList,
                                         final Set<ThemeData> addedThemes,
                                         final boolean fromProject) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        doCollectThemesFromManifest(facet, resultList, addedThemes, fromProject);
      }
    });
  }

  private void doCollectThemesFromManifest(AndroidFacet facet,
                                           List<ThemeData> resultList,
                                           Set<ThemeData> addedThemes,
                                           boolean fromProject) {
    final Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    final Application application = manifest.getApplication();
    if (application == null) {
      return;
    }

    final List<ThemeData> activityThemesList = new ArrayList<ThemeData>();

    final XmlTag applicationTag = application.getXmlTag();
    ThemeData preferredTheme = null;
    if (applicationTag != null) {
      final String applicationThemeRef = applicationTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
      if (applicationThemeRef != null) {
        preferredTheme = getThemeByRef(applicationThemeRef);
      }
    }

    if (preferredTheme == null) {
      final AndroidPlatform platform = AndroidPlatform.getInstance(facet.getModule());
      final IAndroidTarget target = platform != null ? platform.getTarget() : null;
      Configuration configuration = myConfigurationToolBar.getConfiguration();
      if (configuration == null) {
        return;
      }
      final IAndroidTarget renderingTarget = configuration.getTarget();
      final State state = configuration.getDeviceState();
      final ScreenSize screenSize = state.getHardware().getScreen().getSize();
      preferredTheme = getThemeByRef(getDefaultTheme(target, renderingTarget, screenSize));
    }

    if (!addedThemes.contains(preferredTheme) && fromProject == preferredTheme.isProjectTheme()) {
      addedThemes.add(preferredTheme);
      resultList.add(preferredTheme);
    }

    for (Activity activity : application.getActivities()) {
      final XmlTag activityTag = activity.getXmlTag();
      if (activityTag != null) {
        final String activityThemeRef = activityTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
        if (activityThemeRef != null) {
          final ThemeData activityTheme = getThemeByRef(activityThemeRef);
          if (!addedThemes.contains(activityTheme) && fromProject == activityTheme.isProjectTheme()) {
            addedThemes.add(activityTheme);
            activityThemesList.add(activityTheme);
          }
        }
      }
    }

    Collections.sort(activityThemesList);
    resultList.addAll(activityThemesList);
  }

  @NotNull
  private static String getDefaultTheme(IAndroidTarget target, IAndroidTarget renderingTarget, ScreenSize screenSize) {
    final int targetApiLevel = target != null ? target.getVersion().getApiLevel() : 0;

    final int renderingTargetApiLevel = renderingTarget != null ? renderingTarget.getVersion().getApiLevel() : targetApiLevel;

    return targetApiLevel >= 11 && renderingTargetApiLevel >= 11 && screenSize == ScreenSize.XLARGE
           ? SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo"
           : SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme";
  }

  private static void collectProjectThemes(AndroidFacet facet, Collection<ThemeData> resultList, Set<ThemeData> addedThemes) {
    final List<ThemeData> newThemes = new ArrayList<ThemeData>();
    final Map<String, ResourceElement> styleMap = buildStyleMap(facet);

    for (ResourceElement style : styleMap.values()) {
      if (isTheme(style, styleMap, new HashSet<ResourceElement>())) {
        final String themeName = style.getName().getValue();
        if (themeName != null) {
          final ThemeData theme = new ThemeData(themeName, true);
          if (addedThemes.add(theme)) {
            newThemes.add(theme);
          }
        }
      }
    }

    Collections.sort(newThemes);
    resultList.addAll(newThemes);
  }

  private static Map<String, ResourceElement> buildStyleMap(AndroidFacet facet) {
    final Map<String, ResourceElement> result = new HashMap<String, ResourceElement>();
    final List<ResourceElement> styles = facet.getLocalResourceManager().getValueResources(ResourceType.STYLE.getName());
    for (ResourceElement style : styles) {
      final String styleName = style.getName().getValue();
      if (styleName != null) {
        result.put(styleName, style);
      }
    }
    return result;
  }

  private static boolean isTheme(ResourceElement resElement, Map<String, ResourceElement> styleMap, Set<ResourceElement> visitedElements) {
    if (!visitedElements.add(resElement)) {
      return false;
    }

    if (!(resElement instanceof Style)) {
      return false;
    }

    final String styleName = resElement.getName().getValue();
    if (styleName == null) {
      return false;
    }

    final ResourceValue parentStyleRef = ((Style)resElement).getParentStyle().getValue();
    String parentStyleName = null;
    boolean frameworkStyle = false;

    if (parentStyleRef != null) {
      final String s = parentStyleRef.getResourceName();
      if (s != null) {
        parentStyleName = s;
        frameworkStyle = AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(parentStyleRef.getPackage());
      }
    }

    if (parentStyleRef == null) {
      final int index = styleName.indexOf('.');
      if (index >= 0) {
        parentStyleName = styleName.substring(0, index);
      }
    }

    if (parentStyleRef != null) {
      if (frameworkStyle) {
        return parentStyleName.equals("Theme") || parentStyleName.startsWith("Theme.");
      }
      else {
        final ResourceElement parentStyle = styleMap.get(parentStyleName);
        if (parentStyle != null) {
          return isTheme(parentStyle, styleMap, visitedElements);
        }
      }
    }

    return false;
  }

  @NotNull
  private static ThemeData getThemeByRef(@NotNull String themeRef) {
    final boolean isProjectTheme = !themeRef.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX);
    if (themeRef.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
      themeRef = themeRef.substring(SdkConstants.STYLE_RESOURCE_PREFIX.length());
    }
    else if (themeRef.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)) {
      themeRef = themeRef.substring(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX.length());
    }
    return new ThemeData(themeRef, isProjectTheme);
  }

  private class SetThemeAction extends AnAction {
    private final String myTheme;

    public SetThemeAction(@NotNull String theme, boolean select) {
      super(getThemeLabel(theme, false));
      myTheme = theme;
      if (select) {
        getTemplatePresentation().setIcon(AllIcons.Actions.Checked);
      }
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Configuration configuration = myConfigurationToolBar.getConfiguration();
      if (configuration != null) {
        configuration.setTheme(myTheme);
      }
    }
  }
}
