/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.fonts;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import java.util.concurrent.CompletableFuture;
import org.intellij.lang.annotations.Language;

import java.awt.Font;
import java.io.File;
import java.util.List;

/**
 * A {@link DownloadableFontCacheService} provides a cache of downloadable fonts and system fonts.
 * The cache is kept in the users SDK folder. If no SDK is setup a temporary folder is used until a proper SDK is created.
 * Currently there is one known font provider: Google fonts. Support for multiple providers may be added at a later time.
 * This service maintain a sorted list of fonts {@link #getFontFamilies} and holds methods for getting individual fonts.
 */
public interface DownloadableFontCacheService {
  /**
   * Returns a list of downloadable fonts sorted by name.
   * The returned list can be modified without affecting the font cache.
   */
  @NonNull
  List<FontFamily> getFontFamilies();

  /**
   * Returns a list of system fonts sorted by name.
   * The returned list can be modified without affecting the font cache.
   */
  @NonNull
  List<FontFamily> getSystemFontFamilies();

  /**
   * Returns a font file for the menu font (a font representative for the family).
   * This font may have an extra limited char set just enough to display the menu name of the font.
   * Even when this function returns a non null result the font may not be downloaded yet.
   * Or <code>null</code> if no menu file is specified.
   */
  @Nullable
  File getCachedMenuFile(@NonNull FontFamily family);

  /**
   * Returns a font file for the specified font.
   * Even when this function returns a non null result the font may not be downloaded yet.
   * Or <code>null</code> if no menu file is specified.
   */
  @Nullable
  File getCachedFontFile(@NonNull FontDetail family);

  /**
   * Returns XML for a font-family file describing the font.
   * Used in layoutlib for displaying downloadable fonts.
   */
  @Nullable
  @Language("XML")
  String toXml(@NonNull FontFamily family);

  /**
   * Start downloading the specified font without waiting for the outcome.
   */
  CompletableFuture<Boolean> download(@NonNull FontFamily family);

  /**
   * Lookup the {@link FontFamily} of a certain font.
   */
  @Nullable
  FontFamily findFont(@NonNull FontProvider provider, @NonNull String fontName);

  /**
   * Returns a {@link FontFamily} for a named system font or <code>null</code>
   * if no font with the specified name exists.
   */
  @Nullable
  FontFamily getSystemFont(@NonNull String name);

  /**
   * Return a {@link FontFamily} for the default system font which is "sans serif".
   */
  @NonNull
  FontFamily getDefaultSystemFont();

  /**
   * Loads a {@link Font} for displaying the name of the font.
   * The font returned may only contain the glyphs for the font name and may not be able to display other characters.
   */
  @Nullable
  Font loadMenuFont(@NonNull FontFamily fontFamily);

  /**
   * Loads a {@link Font} for general use.
   * The supported character set is dependent on the font which may or may not include latin characters.
   */
  @Nullable
  Font loadDetailFont(@NonNull FontDetail fontDetail);

  /**
   * Will start a download of the most recent downloadable font directory.
   * @param success optional callback after a successful download of a new font directory.
   * @param failure optional callback after a failed download of a font directory.
   */
  void refresh(@Nullable Runnable success, @Nullable Runnable failure);

  /**
   * Returns the path to the current font cache directory, if any.
   */
  @Nullable
  File getFontCachePath();
}
