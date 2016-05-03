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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.createSurface;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.*;

/** Fixture for building up models for tests */
public class ModelBuilder {
  private final ComponentDescriptor myRoot;
  private final AndroidFacet myFacet;
  private final JavaCodeInsightTestFixture myFixture;
  private String myName;

  public ModelBuilder(@NotNull AndroidFacet facet,
                      @NotNull JavaCodeInsightTestFixture fixture,
                      @NotNull String name,
                      @NotNull ComponentDescriptor root) {
    assertTrue(name, name.endsWith(DOT_XML));
    myFacet = facet;
    myFixture = fixture;
    myRoot = root;
    myName = name;
  }

  public ModelBuilder name(@NotNull String name) {
    myName = name;
    return this;
  }

  @Language("XML")
  public String toXml() {
    StringBuilder sb = new StringBuilder(1000);
    myRoot.appendXml(sb, 0);
    return sb.toString();
  }

  @Nullable
  public ComponentDescriptor findById(@NotNull String id) {
    return myRoot.findById(id);
  }

  @Nullable
  public ComponentDescriptor findByPath(@NotNull String... path) {
    return myRoot.findByPath(path);
  }

  @Nullable
  public ComponentDescriptor findByTag(@NotNull String tag) {
    return myRoot.findByTag(tag);
  }

  @Nullable
  public ComponentDescriptor findByBounds(@AndroidCoordinate int x,
                                          @AndroidCoordinate int y,
                                          @AndroidCoordinate int width,
                                          @AndroidCoordinate int height) {
    return myRoot.findByBounds(x, y, width, height);
  }

  public NlModel build() {
    // Creates a design-time version of a model
    final Project project = myFacet.getModule().getProject();
    return WriteCommandAction.runWriteCommandAction(project, (Computable<NlModel>)() -> {
      String xml = toXml();
      try {
        assertNotNull(xml, XmlUtils.parseDocument(xml, true));
      }
      catch (Exception e) {
        fail("Invalid XML created for the model (" + xml + ")");
      }
      String relativePath = "res/layout/" + myName;
      VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(myFixture.getTempDirPath()));
      assertThat(root).isNotNull();
      VirtualFile virtualFile = root.findFileByRelativePath(relativePath);
      XmlFile xmlFile;
      if (virtualFile != null) {
        xmlFile = (XmlFile)PsiManager.getInstance(project).findFile(virtualFile);
        assertThat(xmlFile).isNotNull();
        Document document = PsiDocumentManager.getInstance(project).getDocument(xmlFile);
        assertThat(document).isNotNull();
        document.setText(xml);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      } else {
        xmlFile = (XmlFile)myFixture.addFileToProject(relativePath, xml);
      }
      XmlTag rootTag = xmlFile.getRootTag();
      assertNotNull(xml, rootTag);
      XmlDocument document = xmlFile.getDocument();
      assertNotNull(document);
      NlModel model = SyncNlModel.create(createSurface(), myFixture.getProject(), myFacet, xmlFile);
      model.updateHierarchy(xmlFile.getRootTag(), buildViewInfos(model));
      return model;
    });
  }

  private List<ViewInfo> buildViewInfos(@NotNull NlModel model) {
    List<ViewInfo> infos = Lists.newArrayList();
    XmlFile file = model.getFile();
    assertThat(file).isNotNull();
    assertThat(file.getRootTag()).isNotNull();
    infos.add(myRoot.createViewInfo(null, file.getRootTag()));
    return infos;
  }

  /** Update the given model to reflect the component hierarchy in the given builder */
  public void updateModel(NlModel model, boolean preserveXmlTags) {
    assertThat(model).isNotNull();
    name("linear2.xml"); // temporary workaround: replacing contents not working
    SyncNlModel newModel = (SyncNlModel)(preserveXmlTags ? model : build());
    model.updateHierarchy(newModel.getFile().getRootTag(), buildViewInfos(newModel));
  }
}
