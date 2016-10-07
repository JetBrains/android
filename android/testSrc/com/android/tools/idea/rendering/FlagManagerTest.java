/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.idea.rendering;

import com.android.ide.common.resources.LocaleManager;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Function;
import icons.AndroidIcons;
import junit.framework.TestCase;

import javax.swing.*;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.android.SdkConstants.DOT_PNG;

@SuppressWarnings("javadoc")
public class FlagManagerTest extends TestCase {
  public void testGetFlagImage() {
    FlagManager manager = FlagManager.get();
    Icon us = manager.getFlag("US");
    Icon gb = manager.getFlag("GB");
    Icon ca = manager.getFlag("CA");
    Icon es = manager.getFlag("ES");
    Icon br = manager.getFlag("BR");
    Icon pt = manager.getFlag("PT");
    assertSame(us, manager.getFlag("en", "US"));
    assertSame(gb, manager.getFlag("en", "GB"));
    assertSame(ca, manager.getFlag("en", "CA"));
    Locale.setDefault(Locale.US);
    assertSame(us, manager.getFlag("en", null));
    Locale.setDefault(Locale.UK);
    assertSame(gb, manager.getFlag("en", null));
    Locale.setDefault(Locale.CANADA);
    assertSame(ca, manager.getFlag("en", null));
    assertSame(manager.getFlag("NO"), manager.getFlag("nb", null));
    assertSame(manager.getFlag("FR"), manager.getFlag("fr", null));
    Locale.setDefault(Locale.US);
    assertSame(manager.getFlag("FR"), manager.getFlag("fr", null));

    Locale.setDefault(new Locale("pt", "br"));
    assertSame(br, manager.getFlag("pt", null));
    assertSame(pt, manager.getFlag("pt", "PT"));
    Locale.setDefault(new Locale("pt", "pt"));
    assertSame(pt, manager.getFlag("pt", null));
    assertSame(br, manager.getFlag("pt", "BR"));

    // Special cases where we have custom flags
    assertNotSame(gb, manager.getFlag("cy", null)); // Wales
    assertNotSame(es, manager.getFlag("ca", null)); // Catalonia

    // Aliases - http://developer.android.com/reference/java/util/Locale.html
    assertSame(manager.getFlag("yi", null), manager.getFlag("ji", null));
    assertSame(manager.getFlag("in", null), manager.getFlag("id", null));
    assertSame(manager.getFlag("iw", null), manager.getFlag("he", null));
    assertSame(LocaleManager.getLanguageName("iw"), LocaleManager.getLanguageName("he"));
    assertSame(LocaleManager.getLanguageName("in"), LocaleManager.getLanguageName("id"));
    assertSame(LocaleManager.getLanguageName("yi"), LocaleManager.getLanguageName("ji"));

    assertSame(us, manager.getFlagForFolderName("values-en-rUS"));
    assertSame(gb, manager.getFlagForFolderName("values-en-rGB"));
    Locale.setDefault(Locale.CANADA);
    assertSame(ca, manager.getFlagForFolderName("values-en"));
  }

  public void testNonIso3166DefaultLocale() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=91988
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("es", "419"));
      FlagManager.get().getFlag("es", null);
      Locale.setDefault(new Locale("es", "es"));
      FlagManager.get().getFlag("es", null);
    } finally {
      Locale.setDefault(previous);
    }
  }

  public void testAvailableIcons() {
    // Icons we have from WindowBuilder (which are really the famfamfam icons;
    // see http://www.famfamfam.com/lab/icons/flags)
    String[] icons =
      new String[]{
        "ad", "ae", "af", "ag", "ai", "al", "am", "ao", "ar", "as", "at", "au", "aw", "ax", "az", "ba", "bb", "bd", "be", "bf",
        "bg", "bh", "bi", "bj", "bm", "bn", "bo", "br", "bs", "bt", "bv", "bw", "by", "bz", "ca", "catalonia", "cc", "cd", "cf", "cg", "ch",
        "ci", "ck", "cl", "cm", "cn", "co", "cr", "cu", "cv", "cx", "cy", "cz", "de", "dj", "dk", "dm", "do", "dz", "ec", "ee", "eg", "eh",
        "england", "er", "es", "et", "fi", "fj", "fk", "fm", "fo", "fr", "ga", "gb", "gd", "ge", "gf", "gg", "gh", "gi", "gl", "gm", "gn",
        "gp", "gq", "gr", "gs", "gt", "gu", "gw", "gy", "hk", "hm", "hn", "hr", "ht", "hu", "id", "ie", "il", "im", "in", "io", "iq", "ir",
        "is", "it", "jm", "jo", "jp", "ke", "kg", "kh", "ki", "km", "kn", "kp", "kr", "kw", "ky", "kz", "la", "lb", "lc", "li", "lk", "lr",
        "ls", "lt", "lu", "lv", "ly", "ma", "mc", "md", "me", "mg", "mh", "mk", "ml", "mm", "mn", "mo", "mp", "mq", "mr", "ms", "mt", "mu",
        "mv", "mw", "mx", "my", "mz", "na", "nc", "ne", "nf", "ng", "ni", "nl", "no", "np", "nr", "nu", "nz", "om", "pa", "pe", "pf", "pg",
        "ph", "pk", "pl", "pm", "pn", "pr", "ps", "pt", "pw", "py", "qa", "re", "ro", "rs", "ru", "rw", "sa", "sb", "sc", "scotland", "sd",
        "se", "sg", "sh", "si", "sj", "sk", "sl", "sm", "sn", "so", "sr", "ss", "st", "sv", "sy", "sz", "tc", "td", "tf", "tg", "th", "tj",
        "tk", "tl", "tm", "tn", "to", "tr", "tt", "tv", "tw", "tz", "ua", "ug", "um", "us", "uy", "uz", "va", "vc", "ve", "vg", "vi", "vn",
        "vu", "wales", "wf", "ws", "ye", "yt", "za", "zm", "zw"
      };

    Set<String> sIcons = new HashSet<>(100);
    for (String code : icons) {
      if (code.length() > 2) {
        continue;
      }
      code = code.toUpperCase(Locale.US);
      sIcons.add(code);

      if (!LocaleManager.isValidRegionCode(code)) {
        System.out.println("No region name found for region code " + code);
      }
    }

    Set<String> unused = Sets.newHashSet(LocaleManager.getRegionCodes(false));
    Set<String> reachable = Sets.newHashSet();
    Multimap<String,String> regionToLanguages = ArrayListMultimap.create();
    for (String language : LocaleManager.getLanguageCodes(false)) {
      for (String region : LocaleManager.getRelevantRegions(language)) {
        reachable.add(region);
        regionToLanguages.put(region, language);
      }
    }
    unused.removeAll(reachable);

    for (String region : reachable) {
      if (!sIcons.contains(region)) {
        StringBuilder sb = new StringBuilder();

        sb.append("No icon found for region ").append(region).append("  ").append(LocaleManager.getRegionName(region));
        sb.append(", used for languages ");

        for (String language : regionToLanguages.get(region)) {
          sb.append(language).append("(").append(LocaleManager.getLanguageName(language)).append(") ");
        }
        System.out.println(sb.toString());
      }
    }

    // Known regions that we don't have language to region mappings for
    unused.remove("AQ");
    unused.remove("VA");
    unused.remove("GS");
    unused.remove("TF");
    unused.remove("BV");
    unused.remove("HM");

    if (!unused.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("The following icons are not referenced by any of the " + "language to country bindings:");
      for (String code : unused) {
        sb.append(code.toLowerCase(Locale.US)).append(DOT_PNG).append(" (");
        sb.append(LocaleManager.getRegionName(code)).append(") ");
      }
      System.out.println(sb.toString());
    }
  }

  public void testLanguageNameMapper() {
    Function<Object,String> mapper = FlagManager.getLanguageNameMapper();
    assertEquals("en: English", mapper.fun("en"));
    assertEquals("es: Spanish", mapper.fun("es"));
  }

  public void testRegionNameMapper() {
    Function<Object,String> mapper = FlagManager.getRegionNameMapper();
    assertEquals("US: United States", mapper.fun("US"));
    assertEquals("MX: Mexico", mapper.fun("MX"));
  }

  public void testMissingFlag() {
    Icon icon = FlagManager.get().getFlag("AQ");
    assertNotNull(icon);
    assertSame(AndroidIcons.EmptyFlag, icon);

    IconLoader.STRICT = true;
    icon = FlagManager.get().getFlag("AQ");
    assertNotNull(icon);
    assertSame(AndroidIcons.EmptyFlag, icon);

    icon = FlagManager.get().getFlag("WO"); // Not used in ISO 3166-1
    assertNotNull(icon);
    assertSame(AndroidIcons.EmptyFlag, icon);
  }

  public void testKnownFlag() {
    Icon icon = FlagManager.get().getFlag("US");
    assertNotNull(icon);
    assertNotSame(AndroidIcons.EmptyFlag, icon);
  }
}
