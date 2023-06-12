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
package com.android.tools.idea.common.fixtures;

import static com.android.SdkConstants.DOT_XML;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.DesignSurfaceTestUtil;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.InteractionHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.utils.XmlUtils;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture for building up models for tests
 */
public class ModelBuilder {
  private final ComponentDescriptor myRoot;
  private final AndroidFacet myFacet;
  private final CodeInsightTestFixture myFixture;
  private String myName;
  private final Function2<DesignSurface<? extends SceneManager>, SyncNlModel, SceneManager> myManagerFactory;
  private final Consumer<NlModel> myModelUpdater;
  private final String myPath;
  private final Class<? extends DesignSurface<? extends SceneManager>> mySurfaceClass;
  private final Function1<DesignSurface<? extends SceneManager>, InteractionHandler> myInteractionHandlerCreator;
  @NotNull private final Consumer<NlComponent> myComponentRegistrar;
  private Device myDevice;

  public ModelBuilder(@NotNull AndroidFacet facet,
                      @NotNull CodeInsightTestFixture fixture,
                      @NotNull String name,
                      @NotNull ComponentDescriptor root,
                      @NotNull Function2<DesignSurface<? extends SceneManager>, SyncNlModel, SceneManager> managerFactory,
                      @NotNull Consumer<NlModel> modelUpdater,
                      @NotNull String path,
                      @NotNull Class<? extends DesignSurface<? extends SceneManager>> surfaceClass,
                      @NotNull Function1<DesignSurface<? extends SceneManager>, InteractionHandler> interactionHandlerCreator,
                      @NotNull Consumer<NlComponent> componentRegistrar) {
    assertTrue(name, name.endsWith(DOT_XML));
    myFacet = facet;
    myFixture = fixture;
    myRoot = root;
    myName = name;
    myManagerFactory = managerFactory;
    myModelUpdater = model -> {
      // Reload the change from ComponentDescriptor
      updateXmlToNlModel(model);
      modelUpdater.accept(model);
    };
    myPath = path;
    mySurfaceClass = surfaceClass;
    myInteractionHandlerCreator = interactionHandlerCreator;
    myComponentRegistrar = componentRegistrar;
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

  public ModelBuilder setDevice(@NotNull Device device) {
    myDevice = device;
    return this;
  }

  @NotNull
  public SyncNlModel build() {
    // Creates a design-time version of a model
    final Project project = myFacet.getModule().getProject();
    final SyncNlModel model = buildWithoutSurface();
    return WriteAction.compute(() -> {
      // TODO(b/194482298): Refactor below functions, to create DesignSurface<?> first then add the NlModel.
      DesignSurface<? extends SceneManager> surface = DesignSurfaceTestUtil.createMockSurfaceWithModel(project, project, myManagerFactory,
                                                                               mySurfaceClass, myInteractionHandlerCreator, model);
      model.setDesignSurface(surface);
      return model;
    });
  }

  /**
   * FIXME(b/194482298): Do not create DesignSurface when building the SyncNlModel. This is a temp function for refactoring purpose.
   */
  @Deprecated
  public SyncNlModel buildWithoutSurface() {
    // Creates a design-time version of a model
    final Project project = myFacet.getModule().getProject();
    return WriteAction.compute(() -> {
      String xml = toXml();
      try {
        assertNotNull(xml, XmlUtils.parseDocument(xml, true));
      }
      catch (Exception e) {
        fail("Invalid XML created for the model (" + xml + ")");
      }
      String relativePath = "res/" + myPath + "/" + myName;
      VirtualFile virtualFile = findVirtualFile(relativePath);
      XmlFile xmlFile;
      if (virtualFile != null) {
        xmlFile = (XmlFile)PsiManager.getInstance(project).findFile(virtualFile);
        assertThat(xmlFile).isNotNull();
        Document document = PsiDocumentManager.getInstance(project).getDocument(xmlFile);
        assertThat(document).isNotNull();
        document.setText(xml);
        PsiDocumentManager.getInstance(project).commitAllDocuments();
      }
      else {
        xmlFile = (XmlFile)myFixture.addFileToProject(relativePath, xml);
      }
      XmlTag rootTag = xmlFile.getRootTag();
      assertNotNull(xml, rootTag);
      XmlDocument document = xmlFile.getDocument();
      assertNotNull(document);

      SyncNlModel model =
        SyncNlModel.create(myFixture.getProject(), myComponentRegistrar, null, myFacet, xmlFile.getVirtualFile());
      if (myDevice != null) {
        model.getConfiguration().setDevice(myDevice, true);
      }
      return model;
    });
  }

  @Nullable
  private VirtualFile findVirtualFile(@NotNull String relativePath) {
    if (myFixture instanceof JavaCodeInsightTestFixture) {
      VirtualFile root = LocalFileSystem.getInstance().findFileByIoFile(new File(myFixture.getTempDirPath()));
      assertThat(root).isNotNull();
      return root.findFileByRelativePath(relativePath);
    }
    return null;
  }

  /**
   * Reload the content of the used {@link ComponentDescriptor} for the given {@link NlModel}
   */
  private void updateXmlToNlModel(@NotNull NlModel model) {
    final Project project = model.getProject();
    WriteAction.runAndWait(() -> {
      String xml = toXml();
      // This creates the content from the current ModelRegistrar.
      try {
        assertNotNull(xml, XmlUtils.parseDocument(xml, true));
      }
      catch (Exception e) {
        fail("Invalid XML created for the model (" + xml + ")");
      }
      VirtualFile virtualFile = model.getVirtualFile();
      XmlFile xmlFile = (XmlFile)PsiManager.getInstance(project).findFile(virtualFile);
      assertThat(xmlFile).isNotNull();
      Document document = PsiDocumentManager.getInstance(project).getDocument(xmlFile);
      assertThat(document).isNotNull();
      document.setText(xml);
      PsiDocumentManager.getInstance(project).commitAllDocuments();
    });
  }

  /**
   * Update the given model to reflect the componentHierarchy in the given builder
   */
  public void updateModel(@NotNull NlModel model) {
    assertThat(model).isNotNull();
    myModelUpdater.accept(model);
    for (NlComponent component : model.getComponents()) {
      checkStructure(component);
    }
  }

  private static void checkStructure(NlComponent component) {
    if (NlComponentHelperKt.getHasNlComponentInfo(component)) {
      assertThat(NlComponentHelperKt.getW(component)).isNotEqualTo(-1);
    }
    assertThat(component.getSnapshot()).isNotNull();
    assertThat(component.getTagDeprecated()).isNotNull();
    assertThat(component.getTagName()).isEqualTo(component.getTagDeprecated().getName());

    assertThat(component.getBackend().isValid()).isTrue();
    assertThat(component.getTagDeprecated().getContainingFile()).isEqualTo(component.getModel().getFile());

    for (NlComponent child : component.getChildren()) {
      assertThat(child).isNotSameAs(component);
      assertThat(child.getParent()).isSameAs(component);
      assertThat(child.getTagDeprecated().getParent()).isSameAs(component.getTagDeprecated());

      // Check recursively
      checkStructure(child);
    }
  }

  public static class TestActionManager extends ActionManager<DesignSurface<SceneManager>> {
    public TestActionManager(@NotNull DesignSurface<SceneManager> surface) {
      super(surface);
    }
    @Override
    public void registerActionsShortcuts(@NotNull JComponent component) {}

    @Override
    public DefaultActionGroup getPopupMenuActions(@Nullable NlComponent leafComponent) {
      return new DefaultActionGroup();
    }

    @Override
    public DefaultActionGroup getToolbarActions(List<NlComponent> selection) {
      return new DefaultActionGroup();
    }
  }

}
