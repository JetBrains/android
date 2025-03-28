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
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.LocaleManager;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.Function;
import icons.AndroidIcons;
import icons.StudioIcons;
import java.util.Map;
import javax.swing.Icon;
import javax.swing.ListCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@linkplain FlagManager} provides access to flags for regions known
 * to {@link LocaleManager}. It also contains some locale related display
 * functions.
 * <p>
 * All the flag images came from the WindowBuilder subversion repository
 * http://dev.eclipse.org/svnroot/tools/org.eclipse.windowbuilder/trunk (and in
 * particular, a snapshot of revision 424). However, it appears that the icons
 * are from http://www.famfamfam.com/lab/icons/flags/ which states that "these
 * flag icons are available for free use for any purpose with no requirement for
 * attribution." Adding the URL here such that we can check back occasionally
 * and see if there are corrections or updates. Also note that the flag names
 * are in ISO 3166-1 alpha-2 country codes.
 */
@SuppressWarnings("SpellCheckingInspection")
public class FlagManager {
  private static final FlagManager ourInstance = new FlagManager();

  /**
   * Returns the {@linkplain FlagManager} singleton
   *
   * @return the {@linkplain FlagManager} singleton, never null
   */
  @NotNull
  public static FlagManager get() {
    return ourInstance;
  }

  /**
   * Use the {@link #get()} factory method
   */
  private FlagManager() {
  }

  /**
   * Map from region to flag icon
   */
  @NotNull
  private final Map<String, Icon> myImageMap = Maps.newHashMap();

  /**
   * Returns the flag for the given language and region.
   *
   * @param language the language, or null (if null, region must not be null),
   *                 the 2 letter language code (ISO 639-1), in lower case
   * @param region   the region, or null (if null, language must not be null),
   *                 the 2 letter region code (ISO 3166-1 alpha-2), in upper case
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@Nullable String language, @Nullable String region) {
    assert region != null || language != null;

    if (region == null || region.isEmpty() || region.length() == 3) {
      // No country specified, and the language is for a country we
      // don't have a flag for
      return null;
    }

    return getIcon(region);
  }

  /**
   * Returns the flag for the given language and region.
   *
   * @param configuration the folder configuration
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@NotNull FolderConfiguration configuration) {
    return getFlag(configuration.getLocaleQualifier());
  }

  /**
   * Returns the flag for the given language and region.
   *
   * @param locale the locale qualifier
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@Nullable LocaleQualifier locale) {
    if (locale == null) {
      return null;
    }
    String languageCode = locale.getLanguage();
    String regionCode = locale.getRegion();
    if (LocaleQualifier.FAKE_VALUE.equals(languageCode)) {
      languageCode = null;
    }
    return getFlag(languageCode, regionCode);
  }

  /**
   * Returns a flag for a given resource folder name (such as
   * {@code values-en-rUS}), or null
   *
   * @param folder the folder name
   * @return a corresponding flag icon, or null if none was found
   */
  @Nullable
  public Icon getFlagForFolderName(@NotNull String folder) {
    FolderConfiguration configuration = FolderConfiguration.getConfigForFolder(folder);
    if (configuration != null) {
      return get().getFlag(configuration);
    }

    return null;
  }

  /**
   * Returns the flag for the given region.
   *
   * @param region the 2 letter region code (ISO 3166-1 alpha-2), in upper case
   * @return a suitable flag icon, or null
   */
  @Nullable
  public Icon getFlag(@NotNull String region) {
    assert region.length() == 2 && Character.isUpperCase(region.charAt(0)) && Character.isUpperCase(region.charAt(1)) : region;

    return getIcon(region);
  }

  @Nullable
  private Icon getIcon(@NotNull String base) {
    Icon flagImage = myImageMap.get(base);
    if (flagImage == null) {
      // TODO: Special case locale currently running on system such
      // that the current country matches the current locale
      if (myImageMap.containsKey(base)) {
        // Already checked: there's just no image there
        return null;
      }
      @SuppressWarnings("UnnecessaryFullyQualifiedName")
      String flagFileName = StringUtil.toLowerCase(base) + ".png"; //$NON-NLS-1$
      try {
        flagImage = IconLoader.findResolvedIcon("icons/flags/" + flagFileName, AndroidIcons.class.getClassLoader());
      } catch (Throwable t) {
        // This shouldn't happen in production, but IconLoader.findIcon can throw exceptions
        // when IconLoader.STRICT is set to true, which is the case when running unit tests
        // or with idea.is.internal=true
      }
      if (flagImage == null) {
        flagImage = StudioIcons.LayoutEditor.Toolbar.EMPTY_FLAG;
      }
      myImageMap.put(base, flagImage);
    }

    return flagImage;
  }

  /** Returns a {@link ListCellRenderer} suitable for displaying languages when the list model contains String language codes */
  @NotNull
  public ListCellRenderer getLanguageCodeCellRenderer() {
    final Function<Object, String> nameMapper = getLanguageNameMapper();
    return SimpleListCellRenderer.create((label, value, index) -> {
      label.setText(nameMapper.fun(value));
      label.setIcon(getFlag((String)value, null));
    });
  }

  /** Returns a {@link ListCellRenderer} suitable for displaying regions when the list model contains String region codes */
  @NotNull
  public ListCellRenderer getRegionCodeCellRenderer() {
    final Function<Object, String> nameMapper = getRegionNameMapper();
    return SimpleListCellRenderer.create((label, value, index) -> {
      label.setText(nameMapper.fun(value));
      label.setIcon(getFlag(null, (String)value));
    });
  }

  /** A function which maps from language code to a language label: code + name */
  @NotNull
  public static  Function<Object, String> getLanguageNameMapper() {
    return new Function<Object, String>() {
      @Override
      public String fun(Object value) {
        String languageCode = (String)value;
        if (languageCode.equals(LocaleQualifier.FAKE_VALUE)) {
          return "Any Language";
        }
        String languageName = LocaleManager.getLanguageName(languageCode);
        if (languageName != null && languageName.length() > 30) {
          languageName = languageName.substring(0, 27) + "...";
        }
        return String.format("%1$s: %2$s", languageCode, languageName);
      }
    };
  }

  /** A function which maps from language code to a language label: code + name */
  @NotNull
  public static Function<Object, String> getRegionNameMapper() {
    return new Function<Object, String>() {
      @Override
      public String fun(Object value) {
        String regionCode = (String)value;
        if (regionCode.equals(LocaleQualifier.FAKE_VALUE)) {
          return "Any Region";
        }
        String regionName = LocaleManager.getRegionName(regionCode);
        if (regionName != null && regionName.length() > 30) {
          regionName = regionName.substring(0, 27) + "...";
        }
        return String.format("%1$s: %2$s", regionCode, regionName);
      }
    };
  }

  /**
   * Returns a flag image to use for this locale
   *
   * @return a flag image, or a default globe icon
   */
  @NotNull
  public static Icon getFlagImage(Locale locale) {
    String languageCode = locale.qualifier.hasLanguage() ? locale.qualifier.getLanguage() : null;
    if (languageCode == null) {
      return StudioIcons.LayoutEditor.Toolbar.EMPTY_FLAG;
    }
    String regionCode = locale.hasRegion() ? locale.qualifier.getRegion() : null;
    FlagManager icons = FlagManager.get();
    Icon image = icons.getFlag(languageCode, regionCode);
    if (image == null) {
      image = StudioIcons.LayoutEditor.Toolbar.EMPTY_FLAG;
    }

    return image;
  }
}
