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
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.testing.TestProjectPaths.*;

public class ManifestConflictTest extends AndroidGradleTestCase {

  private HtmlLinkManager myHtmlLinkManager = new HtmlLinkManager();
  private static final Pattern LINK_PATTERN = Pattern.compile("\\<a.*? href=\"(.*?)\".*?\\>", Pattern.CASE_INSENSITIVE);

  public void testResolveAttributeConflict() throws Exception {
    loadProject(MANIFEST_CONFLICT_ATTRIBUTE);
    String[] errors = getErrorHtml();
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  public void testResolveMinSdkConflict() throws Exception {
    loadProject(MANIFEST_CONFLICT_MIN_SDK);
    String[] errors = getErrorHtml();
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  public void testDynamicFeatureExternalDependencyAttributeConflictNotInXml() throws Exception{
    // Load a project with a dynamic feature within an app module and two libraries: lib, lib2.
    // dynamic feature depends on lib2 to bring a node with an attribute that conflicts with the app's merged manifest node.
    // Since the conflicted node does not belong to primary manifest, we expect manifest merger to not return the error position in the
    // error message, and therefore we expect no link generated in the ManifestPanel's error message.
    // See b/269085620 for details. This is a temporary behavior until we fix the way we compute dynamic feature's set of dependency manifests.
    loadProject(MANIFEST_CONFLICT_DYN_FEATURE_ATTR_CONFLICT_NOT_IN_XML);
    var dynFeatureModule = ModuleManager.getInstance(myAndroidFacet.getMainModule().getProject())
      .findModuleByName("testDynamicFeatureExternalDependencyAttributeConflictNotInXml.app.dynamicfeature");
    String[] errors = getErrorHtml(dynFeatureModule);
    assertEquals(1, errors.length);
    assertEmpty(grabHTMLLinks(errors[0]));
  }

  public void testDynamicFeatureExternalDependencyAttributeConflictInXml() throws Exception{
    // Load a project with dynamic feature within an app and a node with conflicting attribute.
    loadProject(MANIFEST_CONFLICT_DYN_FEATURE_ATTR_CONFLICT_IN_XML);
    var dynFeatureModule = ModuleManager.getInstance(myAndroidFacet.getMainModule().getProject())
      .findModuleByName("testDynamicFeatureExternalDependencyAttributeConflictInXml.app.dynamicfeature");
    String[] errors = getErrorHtml(dynFeatureModule);
    assertEquals(1, errors.length);
    clickLink(errors[0], 0);
    assertEquals(0, getErrorHtml().length);
  }

  private void clickLink(String errorHtml, int i) {
    List<String> link = grabHTMLLinks(errorHtml);
    myHtmlLinkManager.handleUrl(link.get(i), myAndroidFacet.getModule(), null, null, false, null);
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

  private String[] getErrorHtml() throws Exception {
    return getErrorHtml(myAndroidFacet.getMainModule());
  }

  private String[] getErrorHtml(Module module) throws Exception {
    MergedManifestSnapshot manifest = MergedManifestManager.getMergedManifest(module).get();
    ImmutableList<MergingReport.Record> records = manifest.getLoggingRecords();
    String[] errors = new String[records.size()];
    for (int c = 0; c < records.size(); c++) {
      MergingReport.Record record = records.get(c);
      errors[c] = ManifestPanel.getErrorHtml(myAndroidFacet, record.getMessage(), record.getSourceLocation(), myHtmlLinkManager, null, true);
    }
    return errors;
  }
}