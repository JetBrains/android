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

import static com.android.tools.idea.testing.TestProjectPaths.MANIFEST_CONFLICT_ATTRIBUTE;
import static com.android.tools.idea.testing.TestProjectPaths.MANIFEST_CONFLICT_DYN_FEATURE_ATTR_CONFLICT_IN_XML;
import static com.android.tools.idea.testing.TestProjectPaths.MANIFEST_CONFLICT_DYN_FEATURE_ATTR_CONFLICT_NOT_IN_XML;
import static com.android.tools.idea.testing.TestProjectPaths.MANIFEST_CONFLICT_MIN_SDK;
import static com.google.common.truth.Truth.assertThat;

import com.android.manifmerger.MergingReport;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Rule;
import org.junit.Test;

public class ManifestConflictTest {
  @Rule
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  private final ManifestPanel.HtmlLinkManager myHtmlLinkManager = new ManifestPanel.HtmlLinkManager();

  private static final Pattern LINK_PATTERN = Pattern.compile("\\<a.*? href=\"(.*?)\".*?\\>", Pattern.CASE_INSENSITIVE);

  @Test
  public void testResolveAttributeConflict() throws Exception {
    projectRule.loadProject(MANIFEST_CONFLICT_ATTRIBUTE);
    Module module = projectRule.getModule("testResolveAttributeConflict.main");
    String[] errors = getErrorHtml(module);
    assertThat(errors).hasLength(1);
    clickLink(module, errors[0], 0);
    assertThat(getErrorHtml(module)).hasLength(0);
  }

  @Test
  public void testResolveMinSdkConflict() throws Exception {
    projectRule.loadProject(MANIFEST_CONFLICT_MIN_SDK);
    Module module = projectRule.getModule("testResolveMinSdkConflict.main");
    String[] errors = getErrorHtml(module);
    assertThat(errors).hasLength(1);
    clickLink(module, errors[0], 0);
    assertThat(getErrorHtml(module)).hasLength(0);
  }

  @Test
  public void testDynamicFeatureExternalDependencyAttributeConflictNotInXml() throws Exception{
    // Load a project with a dynamic feature within an app module and two libraries: lib, lib2.
    // dynamic feature depends on lib2 to bring a node with an attribute that conflicts with the app's merged manifest node.
    // Since the conflicted node does not belong to primary manifest, we expect manifest merger to not return the error position in the
    // error message, and therefore we expect no link generated in the ManifestPanel's error message.
    // See b/269085620 for details. This is a temporary behavior until we fix the way we compute dynamic feature's set of dependency manifests.
    projectRule.loadProject(MANIFEST_CONFLICT_DYN_FEATURE_ATTR_CONFLICT_NOT_IN_XML);
    var dynFeatureModule = projectRule.getModule("testDynamicFeatureExternalDependencyAttributeConflictNotInXml.app.dynamicfeature");
    String[] errors = getErrorHtml(dynFeatureModule);
    assertThat(errors).hasLength(1);
    assertThat(grabHTMLLinks(errors[0])).isEmpty();
  }

  @Test
  public void testDynamicFeatureExternalDependencyAttributeConflictInXml() throws Exception{
    // Load a project with dynamic feature within an app and a node with conflicting attribute.
    projectRule.loadProject(MANIFEST_CONFLICT_DYN_FEATURE_ATTR_CONFLICT_IN_XML);
    var dynFeatureModule = projectRule.getModule("testDynamicFeatureExternalDependencyAttributeConflictInXml.app.dynamicfeature");
    String[] errors = getErrorHtml(dynFeatureModule);
    assertThat(errors).hasLength(1);
    clickLink(dynFeatureModule, errors[0], 0);
    assertThat(getErrorHtml(dynFeatureModule)).hasLength(0);
  }

  private void clickLink(Module module, String errorHtml, int i) {
    List<String> link = grabHTMLLinks(errorHtml);
    myHtmlLinkManager.handleUrl(link.get(i), module, null);
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

  private String[] getErrorHtml(Module module) throws Exception {
    Project project = module.getProject();
    AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
    ManifestPanelToken<AndroidProjectSystem> token = ManifestPanelToken.EP_NAME.getExtensionList().stream()
      .filter(it -> it.isApplicable(projectSystem)).findFirst().orElse(null);
    MergedManifestSnapshot manifest = MergedManifestManager.getMergedManifest(module).get();
    ImmutableList<MergingReport.Record> records = manifest.getLoggingRecords();
    String[] errors = new String[records.size()];
    for (int c = 0; c < records.size(); c++) {
      MergingReport.Record record = records.get(c);
      errors[c] = ReadAction.compute(() -> {
        return ManifestPanel.getErrorHtml(AndroidFacet.getInstance(module), record.getMessage(), record.getSourceLocation(),
                                          myHtmlLinkManager, token, null, true);
      });
    }
    return errors;
  }
}