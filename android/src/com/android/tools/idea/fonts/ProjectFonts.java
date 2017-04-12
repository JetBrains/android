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
package com.android.tools.idea.fonts;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.resources.ResourceType;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.fonts.FontDetail.DEFAULT_BOLD_WEIGHT;
import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WEIGHT;
import static com.android.tools.idea.fonts.FontDetail.DEFAULT_WIDTH;
import static com.android.tools.idea.fonts.FontFamily.FontSource.LOOKUP;
import static com.android.tools.idea.fonts.FontFamily.FontSource.PROJECT;

/**
 * This class will find all the project level font definitions by iterating
 * over the fonts found in the resources.
 * For font-family files there is code to detect circular references and resolve
 * each font-family to a TTF file that can be shown in a UI.
 */
public class ProjectFonts {
  private final DownloadableFontCacheServiceImpl myService;
  private final ResourceResolver myResolver;
  private final ResourceValueMap myFonts;
  private final Map<String, FontFamily> myProjectFonts;
  private final Map<String, FontFamilyParser.ParseResult> myParseResults;
  private final List<String> myDefinitions;

  public ProjectFonts(@NotNull ResourceResolver resolver) {
    myService = DownloadableFontCacheServiceImpl.getInstance();
    myResolver = resolver;
    myFonts = resolver.getProjectResources().get(ResourceType.FONT);
    myProjectFonts = new TreeMap<>();
    myParseResults = new HashMap<>();
    myDefinitions = new ArrayList<>();
  }

  /**
   * Return a list of {@link FontFamily} defined in the project.
   */
  public List<FontFamily> getFonts() {
    List<FontFamily> fonts = new ArrayList<>();
    List<String> names = myFonts.keySet().stream().sorted().collect(Collectors.toList());
    for (String name : names) {
      fonts.add(resolveFont("@font/" + name));
    }
    return fonts;
  }

  /**
   * Return the font family for a given name.
   */
  @Nullable
  public FontFamily getFont(@NotNull String name) {
    return resolveFont(name);
  }

  /**
   * Return the error message associated with a given font family.
   * Or <code>null</code> if there isn't any error.
   */
  @Nullable
  public String getErrorMessage(@Nullable FontFamily family) {
    if (family == null || !family.getMenu().isEmpty()) {
      return null;
    }
    String name = "@font/" + family.getName();
    analyzeFont(name);
    if (hasCircularReferences(name)) {
      return "The font: \"" + family.getName() + "\" has a circular definition";
    }
    return "The font: \"" + family.getName() + "\" has an error in the definition";
  }

  /**
   * Resolve a project font name to the cached font file (usually a ttf file).
   * This method is meant to be used from layoutlib to resolve a downloadable font.
   * Callers should be able to handle corrupt font files.
   *
   * @param name      the name of the font starting with "@font/"
   * @param textStyle the textStyle wanted of this font
   * @return the font file specified by the {@param name} defaulting to the {@param textStyle} attribute specified
   * or <code>null</code> if {@param name} font or its cached file doesn't exists.
   */
  @Nullable
  public File getFontFile(@NotNull String name, @Nullable String textStyle) {
    int weight = textStyle != null && textStyle.contains("bold") ? DEFAULT_BOLD_WEIGHT : DEFAULT_WEIGHT;
    boolean italics = textStyle != null && textStyle.contains("italic");
    FontDetail.Builder wanted = new FontDetail.Builder(weight, DEFAULT_WIDTH, italics, "", null);
    FontFamily family = resolveFont(name);
    if (family.getMenu().isEmpty()) {  // Error in definition
      return null;
    }
    FontDetail best = findBestMatch(family.getFonts(), wanted);
    if (best == null) {
      return null;
    }
    return best.getCachedFontFile();
  }

  /**
   * Given a font resource name e.g. "@font/myfont" find the {@link FontFamily} it is referring to.
   * The result can be either:
   * <ul>
   *   <li>A reference to a system font</li>
   *   <li>A reference to a ttf file in the project.</li>
   *   <li>A reference to a downloadable font i.e. a <font-family/> xml file with a font query attribute.</li>
   *   <li>A reference to a compound <font-family/> xml file with references to other font resource names.</li>
   * </ul>
   *
   * If the resource name is a reference to a compound font family xml file, we will have to resolve the
   * resource names specified in the xml file. This code needs to deal with errors like a recursive defined
   * compound font specification.
   *
   * @param name name of font resource used as value of android:fontFamily e.g. "@font/myfont" or "cursive"
   * @return {@link FontFamily} describing the font.
   */
  @NotNull
  private FontFamily resolveFont(@NotNull String name) {
    analyzeFont(name);
    FontFamily resolvedFamily = myProjectFonts.get(name);
    if (resolvedFamily != null) {
      return resolvedFamily;
    }
    if (isSystemFont(name)) {
      // Don't store system fonts in myProjectFonts.
      // Instead: simply return the FontFamily from the system font cache.
      FontFamily family = myService.getSystemFont(name);
      if (family == null) {
        family = myService.getDefaultSystemFont();
      }
      return family;
    }
    FontFamilyParser.ParseResult result = myParseResults.get(name);
    assert result != null;
    if (result.getFonts().isEmpty()) {
      return resolveDownloadableFont(name, result);
    }
    else {
      return resolveCompoundFont(name, result);
    }
  }

  private FontFamily resolveDownloadableFont(@NotNull String name, @NotNull FontFamilyParser.ParseResult result) {
    FontProvider provider = new FontProvider("", result.getAuthority(), "");
    FontDetail.Builder wanted = result.getFontDetail();
    FontFamily wantedFamily = new FontFamily(provider, LOOKUP, result.getFontName(), "", null, Collections.singletonList(wanted));
    FontFamily family = myService.lookup(wantedFamily);
    if (family == null) {
      return createUnresolvedFontFamily(name);
    }
    FontDetail best = findBestMatch(family.getFonts(), wanted);
    if (best == null) {
      return createUnresolvedFontFamily(name);
    }
    return createSynonym(name, best);
  }

  private FontFamily resolveCompoundFont(@NotNull String name, @NotNull FontFamilyParser.ParseResult result) {
    if (hasCircularReferences(name)) {
      return createUnresolvedFontFamily(name);
    }
    List<FontDetail> fonts = new ArrayList<>();
    for (Map.Entry<String, FontDetail.Builder> font : result.getFonts().entrySet()) {
      String dependency = font.getKey();
      FontDetail.Builder wanted = font.getValue();
      FontFamily family = resolveFont(dependency);
      if (family.getMenu().isEmpty()) {
        return createUnresolvedFontFamily(name);
      }
      FontDetail best = findBestMatch(family.getFonts(), wanted);
      assert best != null;
      best = new FontDetail(best, wanted);
      fonts.add(best);
    }
    return createCompoundFamily(name, fonts);
  }

  @Nullable
  private static FontDetail findBestMatch(@NotNull List<FontDetail> fonts, @NotNull FontDetail.Builder wanted) {
    FontDetail best = null;
    int bestMatch = Integer.MAX_VALUE;
    for (FontDetail detail : fonts) {
      int match = detail.match(wanted);
      if (match < bestMatch) {
        bestMatch = match;
        best = detail;
        if (match == 0) {
          break;
        }
      }
    }
    return best;
  }

  /**
   * Analyze a font resource name.<br/>
   * The result of the analysis is either:
   * <ul>
   *   <li>The parse result from an XML <font-family/> file. Any font references are also analyzed.</li>
   *   <li>A resolved {@link FontFamily} if the font is an embedded ttf file.</li>
   *   <li>An unresolved fake {@link FontFamily} if it is impossible to resolve the resource name.</li>
   * </ul>
   * @param name name of font resource used as value of android:fontFamily e.g. "@font/myfont" or "cursive"
   */
  private void analyzeFont(@NotNull String name) {
    if (isKnownFont(name)) {
      return;
    }
    String fontName = StringUtil.trimStart(name, "@font/");
    ResourceValue resourceValue = myFonts.get(fontName);
    if (resourceValue == null) {
      createUnresolvedFontFamily(name);
      return;
    }
    ResourceValue resolvedValue = myResolver.resolveResValue(resourceValue);
    resourceValue = resolvedValue != null ? resolvedValue : resourceValue;
    String value = resourceValue.getValue();
    if (value == null) {
      createUnresolvedFontFamily(name);
      return;
    }
    if (value.endsWith(".xml")) {
      FontFamilyParser.ParseResult result = FontFamilyParser.parseFontFamily(new File(value));
      if (result != null) {
        myParseResults.put(name, result);
        for (String font : result.getFonts().keySet()) {
          analyzeFont(font);
        }
      }
      else {
        createUnresolvedFontFamily(name);
      }
      return;
    }
    createEmbeddedFontFamily(name, value);
  }

  private boolean isKnownFont(@NotNull String name) {
    return isSystemFont(name) || myProjectFonts.containsKey(name) || myParseResults.containsKey(name);
  }

  private static boolean isSystemFont(@NotNull String name) {
    return !name.startsWith("@font/");
  }

  private boolean hasCircularReferences(@NotNull String name) {
    myDefinitions.clear();
    myDefinitions.add(name);
    return checkDependencies(name);
  }

  private boolean checkDependencies(@NotNull String name) {
    FontFamilyParser.ParseResult result = myParseResults.get(name);
    if (result == null) {
      return false;
    }
    int dept = myDefinitions.size();
    for (String dependency : result.getFonts().keySet()) {
      myDefinitions.subList(dept, myDefinitions.size()).clear();
      if (myDefinitions.contains(dependency)) {
        return true;
      }
      myDefinitions.add(dependency);
      if (checkDependencies(dependency)) {
        return true;
      }
    }
    return false;
  }

  private void createEmbeddedFontFamily(@NotNull String name, @NotNull String fileName) {
    String fontName = StringUtil.trimStart(name, "@font/");
    String fileUrl = FontFamily.FILE_PROTOCOL_START + fileName;
    FontDetail.Builder detail = new FontDetail.Builder(DEFAULT_WEIGHT, DEFAULT_WIDTH, false, fileUrl, null);
    FontFamily family = new FontFamily(FontProvider.EMPTY_PROVIDER, PROJECT, fontName, fileUrl, null, Collections.singletonList(detail));
    myProjectFonts.put(name, family);
  }

  private FontFamily createUnresolvedFontFamily(@NotNull String name) {
    String fontName = StringUtil.trimStart(name, "@font/");
    FontDetail.Builder detail = new FontDetail.Builder(DEFAULT_WEIGHT, DEFAULT_WIDTH, false, "", null);
    FontFamily family = new FontFamily(FontProvider.EMPTY_PROVIDER, PROJECT, fontName, "", null, Collections.singletonList(detail));
    myProjectFonts.put(name, family);
    return family;
  }

  private FontFamily createSynonym(@NotNull String name, @NotNull FontDetail detail) {
    String fontName = StringUtil.trimStart(name, "@font/");
    FontFamily original = detail.getFamily();
    FontDetail.Builder synonym = new FontDetail.Builder(detail);
    FontFamily family =
      new FontFamily(original.getProvider(), PROJECT, fontName, original.getMenu(), null, Collections.singletonList(synonym));
    myProjectFonts.put(name, family);
    return family;
  }

  private FontFamily createCompoundFamily(@NotNull String name, @NotNull List<FontDetail> fonts) {
    assert !fonts.isEmpty();
    String fontName = StringUtil.trimStart(name, "@font/");
    FontDetail.Builder wanted = new FontDetail.Builder();
    FontDetail best = findBestMatch(fonts, wanted);
    assert best != null;
    FontFamily original = best.getFamily();
    FontFamily family = FontFamily.createCompound(original.getProvider(), PROJECT, fontName, original.getMenu(), fonts);
    myProjectFonts.put(name, family);
    return family;
  }
}
