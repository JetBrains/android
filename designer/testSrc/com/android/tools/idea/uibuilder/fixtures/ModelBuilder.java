/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.fixtures;

import com.android.tools.idea.uibuilder.SyncNlModel;
import org.jetbrains.annotations.NotNull;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;

import java.util.List;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.createSurface;

/** Fixture for building up models for tests */
public class ModelBuilder {
  private final String myName;
  private final ComponentDescriptor myRoot;
  private final AndroidFacet myFacet;
  private final JavaCodeInsightTestFixture myFixture;

  public ModelBuilder(@NotNull AndroidFacet facet,
                      @NotNull JavaCodeInsightTestFixture fixture,
                      @NotNull String name,
                      @NotNull ComponentDescriptor root) {
    TestCase.assertTrue(name, name.endsWith(DOT_XML));
    myFacet = facet;
    myFixture = fixture;
    myRoot = root;
    myName = name;
  }

  @Language("XML")
  public String toXml() {
    StringBuilder sb = new StringBuilder(1000);
    myRoot.appendXml(sb, 0);
    return sb.toString();
  }

  public NlModel build() {
    // Creates a design-time version of a model
    return WriteCommandAction.runWriteCommandAction(myFacet.getModule().getProject(), (Computable<NlModel>)() -> {
      String xml = toXml();
      try {
        TestCase.assertNotNull(xml, XmlUtils.parseDocument(xml, true));
      }
      catch (Exception e) {
        TestCase.fail("Invalid XML created for the model (" + xml + ")");
      }
      XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("res/layout/" + myName, xml);
      XmlTag rootTag = xmlFile.getRootTag();
      TestCase.assertNotNull(xml, rootTag);

      List<ViewInfo> infos = Lists.newArrayList();
      infos.add(myRoot.createViewInfo(null, rootTag));
      XmlDocument document = xmlFile.getDocument();
      TestCase.assertNotNull(document);
      NlModel model = SyncNlModel.create(createSurface(), myFixture.getProject(), myFacet, xmlFile);
      model.updateHierarchy(infos);
      return model;
    });
  }
}
