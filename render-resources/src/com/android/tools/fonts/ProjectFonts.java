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

import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WEIGHT;
import static com.android.ide.common.fonts.FontDetailKt.DEFAULT_WIDTH;
import static com.android.ide.common.fonts.FontFamilyKt.FILE_PROTOCOL_START;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.fonts.FontSource;
import com.android.ide.common.fonts.MutableFontDetail;
import com.android.ide.common.fonts.QueryParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.tools.apk.analyzer.ResourceIdResolver;
import com.android.tools.res.FileResourceReader;
import com.android.tools.res.ResourceRepositoryManager;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import kotlin.text.StringsKt;

/**
 * This class will find all the project level font definitions by iterating
 * over the fonts found in the resources.
 * For font-family files there is code to detect circular references and resolve
 * each font-family to a TTF file that can be shown in a UI.
 */
public class ProjectFonts {
  private final DownloadableFontCacheService myService;
  private final ResourceRepositoryManager myResourceRepository;
  private final ResourceIdResolver myResourceIdResolver;
  private final Map<String, FontFamily> myProjectFonts;
  private final Map<String, QueryParser.ParseResult> myParseResults;
  private final List<String> myDefinitions;

  public ProjectFonts(
    @NonNull DownloadableFontCacheService fontService,
    @NonNull ResourceRepositoryManager resourceRepositoryManager) {
    this(fontService, resourceRepositoryManager, ResourceIdResolver.NO_RESOLUTION);
  }

  public ProjectFonts(
    @NonNull DownloadableFontCacheService fontService,
    @NonNull ResourceRepositoryManager resourceRepositoryManager,
    @NonNull ResourceIdResolver resourceIdResolver) {
    myService = fontService;
    myResourceRepository = resourceRepositoryManager;
    myResourceIdResolver = resourceIdResolver;
    myProjectFonts = new TreeMap<>();
    myParseResults = new HashMap<>();
    myDefinitions = new ArrayList<>();
  }

  /**
   * Return a list of {@link FontFamily} defined in the project.
   */
  @NonNull
  public List<FontFamily> getFonts() {
    List<FontFamily> fonts = new ArrayList<>();
    ListMultimap<String, ResourceItem>
      fontResources = myResourceRepository.getProjectResources().getResources(ResourceNamespace.TODO(), ResourceType.FONT);
    List<String> names = fontResources.keySet().stream().sorted().collect(Collectors.toList());
    for (String name : names) {
      fonts.add(resolveFont("@font/" + name));
    }
    return fonts;
  }

  /**
   * Return the font family for a given name.
   */
  @NonNull
  public FontFamily getFont(@NonNull String name) {
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
  @NonNull
  private FontFamily resolveFont(@NonNull String name) {
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
    QueryParser.ParseResult result = myParseResults.get(name);
    if (result instanceof QueryParser.DownloadableParseResult) {
      return resolveDownloadableFont(name, (QueryParser.DownloadableParseResult)result);
    }
    if (result instanceof FontFamilyParser.CompoundFontResult) {
      return resolveCompoundFont(name, (FontFamilyParser.CompoundFontResult)result);
    }
    return createUnresolvedFontFamily(name);
  }

  private FontFamily resolveDownloadableFont(@NonNull String name, @NonNull QueryParser.DownloadableParseResult result) {
    String authority = result.getAuthority();
    List<FontDetail> details = new ArrayList<>();
    for (Map.Entry<String, Collection<MutableFontDetail>> entry : result.getFonts().asMap().entrySet()) {
      String fontName = entry.getKey();
      FontProvider provider = new FontProvider("", authority, "", "", "", "");
      FontFamily family = myService.findFont(provider, fontName);
      if (family == null) {
        return createUnresolvedFontFamily(name);
      }
      for (MutableFontDetail wanted : entry.getValue()) {
        FontDetail best = wanted.findBestMatch(family.getFonts());
        if (best == null) {
          return createUnresolvedFontFamily(name);
        }
        if (details.indexOf(best) < 0) {
          details.add(best);
        }
      }
    }
    if (details.isEmpty()) {
      return createUnresolvedFontFamily(name);
    }
    return createSynonym(name, details);
  }

  private FontFamily resolveCompoundFont(@NonNull String name, @NonNull FontFamilyParser.CompoundFontResult result) {
    if (hasCircularReferences(name)) {
      return createUnresolvedFontFamily(name);
    }
    List<FontDetail> fonts = new ArrayList<>();
    for (Map.Entry<String, MutableFontDetail> font : result.getFonts().entrySet()) {
      String dependency = font.getKey();
      MutableFontDetail wanted = font.getValue();
      FontFamily family = resolveFont(dependency);
      if (family.getMenu().isEmpty()) {
        return createUnresolvedFontFamily(name);
      }
      FontDetail best = wanted.findBestMatch(family.getFonts());
      assert best != null;
      best = new FontDetail(best, wanted);
      fonts.add(best);
    }
    return createCompoundFamily(name, fonts);
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
  private void analyzeFont(@NonNull String name) {
    if (isKnownFont(name)) {
      return;
    }
    ListMultimap<String, ResourceItem> fonts =
      myResourceRepository.getAppResources().getResources(ResourceNamespace.TODO(), ResourceType.FONT);
    String fontName = StringsKt.removePrefix(name, "@font/");
    if (!fonts.keySet().contains(fontName)) {
      createUnresolvedFontFamily(name);
      return;
    }

    List<ResourceItem> items = fonts.get(fontName);
    ResourceValue resourceValue = items.stream().map(item -> item.getResourceValue()).findFirst().orElse(null);
    if (resourceValue == null) {
      return;
    }
    String value = resourceValue.getValue();
    if (value == null) {
      createUnresolvedFontFamily(name);
      return;
    }
    if (value.endsWith(".xml")) {
      PathString pathString = ResourcesUtil.toFileResourcePathString(value);
      if (pathString == null) {
        createUnresolvedFontFamily(name);
        return;
      }
      byte[] fileBytes;
      try {
        fileBytes = FileResourceReader.readBytes(pathString, myResourceIdResolver);
      } catch (IOException ignore) {
        createUnresolvedFontFamily(name);
        return;
      }

      InputStream is = new ByteArrayInputStream(fileBytes);
      QueryParser.ParseResult result = FontFamilyParser.parseFontFamily(is, value);
      if (result instanceof FontFamilyParser.ParseErrorResult) {
        createUnresolvedFontFamily(name);
        return;
      }
      myParseResults.put(name, result);
      if (result instanceof FontFamilyParser.CompoundFontResult) {
        FontFamilyParser.CompoundFontResult compoundResult = (FontFamilyParser.CompoundFontResult)result;
        for (String font : compoundResult.getFonts().keySet()) {
          analyzeFont(font);
        }
      }
      return;
    }
    createEmbeddedFontFamily(name, value);
  }

  private boolean isKnownFont(@NonNull String name) {
    return isSystemFont(name) || myProjectFonts.containsKey(name) || myParseResults.containsKey(name);
  }

  private static boolean isSystemFont(@NonNull String name) {
    return !name.startsWith("@font/");
  }

  private boolean hasCircularReferences(@NonNull String name) {
    myDefinitions.clear();
    myDefinitions.add(name);
    return checkDependencies(name);
  }

  private boolean checkDependencies(@NonNull String name) {
    QueryParser.ParseResult result = myParseResults.get(name);
    if (result == null) {
      return false;
    }
    int dept = myDefinitions.size();
    if (result instanceof FontFamilyParser.CompoundFontResult) {
      FontFamilyParser.CompoundFontResult compoundResult = (FontFamilyParser.CompoundFontResult)result;
      for (String dependency : compoundResult.getFonts().keySet()) {
        myDefinitions.subList(dept, myDefinitions.size()).clear();
        if (myDefinitions.contains(dependency)) {
          return true;
        }
        myDefinitions.add(dependency);
        if (checkDependencies(dependency)) {
          return true;
        }
      }
    }
    return false;
  }

  private void createEmbeddedFontFamily(@NonNull String name, @NonNull String fileName) {
    String fontName = StringsKt.removePrefix(name, "@font/");
    String fileUrl = FILE_PROTOCOL_START + fileName;
    MutableFontDetail detail = new MutableFontDetail(DEFAULT_WEIGHT, DEFAULT_WIDTH, false, fileUrl, "", false, false);
    FontFamily family = new FontFamily(FontProvider.EMPTY_PROVIDER, FontSource.PROJECT, fontName, fileUrl, "", Collections.singletonList(detail));
    myProjectFonts.put(name, family);
  }

  private FontFamily createUnresolvedFontFamily(@NonNull String name) {
    String fontName = StringsKt.removePrefix(name, "@font/");
    MutableFontDetail detail = new MutableFontDetail(DEFAULT_WEIGHT, DEFAULT_WIDTH, false);
    FontFamily family = new FontFamily(FontProvider.EMPTY_PROVIDER, FontSource.PROJECT, fontName, "", "", Collections.singletonList(detail));
    myProjectFonts.put(name, family);
    return family;
  }

  private FontFamily createSynonym(@NonNull String name, @NonNull List<FontDetail> details) {
    assert !details.isEmpty();
    MutableFontDetail wanted = new MutableFontDetail(400, 100, false);
    FontDetail best = wanted.findBestMatch(details);
    assert best != null;
    FontProvider provider = best.getFamily().getProvider();
    String fontName = StringsKt.removePrefix(name, "@font/");
    FontFamily family = new FontFamily(provider, FontSource.PROJECT, fontName, best.getFontUrl(), "", ImmutableList.copyOf(details));
    myProjectFonts.put(name, family);
    return family;
  }

  private FontFamily createCompoundFamily(@NonNull String name, @NonNull List<FontDetail> fonts) {
    assert !fonts.isEmpty();
    String fontName = StringsKt.removePrefix(name, "@font/");
    MutableFontDetail wanted = new MutableFontDetail(400, 100, false);
    FontDetail best = wanted.findBestMatch(fonts);
    assert best != null;
    FontFamily original = best.getFamily();
    FontFamily family = new FontFamily(
      original.getProvider(), FontSource.PROJECT, fontName, original.getMenu(), "", ImmutableList.copyOf(fonts));
    myProjectFonts.put(name, family);
    return family;
  }
}
