/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.manifest;

import com.android.manifmerger.MergingReport;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ManifestConflictTest extends AndroidGradleTestCase {

  private HtmlLinkManager myHtmlLinkManager = new HtmlLinkManager();
  private static final Pattern LINK_PATTERN = Pattern.compile("\\<a.*? href=\"(.*?)\".*?\\>", Pattern.CASE_INSENSITIVE);

  public void testResolveAttributeConflict() throws Exception {
    loadProject("projects/manifestConflict/attribute");
    String[] errors = getErrorHtml();
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  public void testResolveBuildPackageConflict() throws Exception {
    loadProject("projects/manifestConflict/buildPackage");
    String[] errors = getErrorHtml();
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  public void testResolveFlavorPackageConflict() throws Exception {
    loadProject("projects/manifestConflict/flavorPackage");
    String[] errors = getErrorHtml();
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  public void testResolveMinSdkConflict() throws Exception {
    loadProject("projects/manifestConflict/minSdk");
    String[] errors = getErrorHtml();
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  private void clickLink(String errorHtml, int i) {
    List<String> link = grabHTMLLinks(errorHtml);
    myHtmlLinkManager.handleUrl(link.get(i), myAndroidFacet.getModule(), null, null, null);
  }

  public static List<String> grabHTMLLinks(String html) {
    List<String> result = new ArrayList<>();
    Matcher matcherTag = LINK_PATTERN.matcher(html);
    while (matcherTag.find()) {
      String link = matcherTag.group(1);
      result.add(link);
    }
    return result;
  }

  private String[] getErrorHtml() {
    MergedManifest manifest = MergedManifest.get(myAndroidFacet);
    manifest.clear();
    ImmutableList<MergingReport.Record> records = manifest.getLoggingRecords();
    String[] errors = new String[records.size()];
    for (int c = 0; c < records.size(); c++) {
      MergingReport.Record record = records.get(c);
      errors[c] = ManifestPanel.getErrorHtml(myAndroidFacet, record.getMessage(), record.getSourceLocation(), myHtmlLinkManager, null);
    }
    return errors;
  }
}