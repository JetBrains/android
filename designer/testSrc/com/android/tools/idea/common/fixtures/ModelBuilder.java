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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.DefaultSelectionModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.InteractionHandler;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.TestActionManager;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JPanel;
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
  private final Function<? super SyncNlModel, ? extends SceneManager> myManagerFactory;
  private final BiConsumer<? super NlModel, ? super NlModel> myModelUpdater;
  private final String myPath;
  private final Class<? extends DesignSurface> mySurfaceClass;
  private final Function<DesignSurface, InteractionHandler> myInteractionProviderCreator;
  @NotNull private final Consumer<NlComponent> myComponentConsumer;
  private Device myDevice;
  private String myModelDisplayName;

  public ModelBuilder(@NotNull AndroidFacet facet,
                      @NotNull CodeInsightTestFixture fixture,
                      @NotNull String name,
                      @NotNull ComponentDescriptor root,
                      @NotNull Function<? super SyncNlModel, ? extends SceneManager> managerFactory,
                      @NotNull BiConsumer<? super NlModel, ? super NlModel> modelUpdater,
                      @NotNull String path,
                      @NotNull Class<? extends DesignSurface> surfaceClass,
                      @NotNull Function<DesignSurface, InteractionHandler> interactionProviderCreator,
                      @NotNull Consumer<NlComponent> componentRegistrar) {
    assertTrue(name, name.endsWith(DOT_XML));
    myFacet = facet;
    myFixture = fixture;
    myRoot = root;
    myName = name;
    myManagerFactory = managerFactory;
    myModelUpdater = modelUpdater;
    myPath = path;
    mySurfaceClass = surfaceClass;
    myInteractionProviderCreator = interactionProviderCreator;
    myComponentConsumer = componentRegistrar;
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
  public ModelBuilder setModelDisplayName(@NotNull String displayName) {
    myModelDisplayName = displayName;
    return this;
  }

  public SyncNlModel build() {
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

      DesignSurface surface = createSurface(project, mySurfaceClass, myInteractionProviderCreator);
      when(surface.getComponentRegistrar()).thenReturn(myComponentConsumer);
      SyncNlModel model = SyncNlModel.create(surface, myFixture.getProject(), myModelDisplayName, myFacet, xmlFile.getVirtualFile());
      when(surface.getModel()).thenReturn(model);
      when(surface.getModels()).thenReturn(ImmutableList.of(model));
      when(surface.getConfiguration()).thenReturn(model.getConfiguration());
      when(surface.getConfigurations()).thenReturn(ImmutableList.of(model.getConfiguration()));

      // TODO: NlDesignSurface should not be referenced from here.
      // TODO: Do we need a special version of ModelBuilder for Nele?
      if (mySurfaceClass.equals(NlDesignSurface.class)) {
        when(((NlDesignSurface)surface).getScreenViewProvider()).thenReturn(NlScreenViewProvider.BLUEPRINT);
        when(surface.getActionHandlerProvider()).thenReturn(NlDesignSurface::defaultActionHandlerProvider);
      }

      SceneManager sceneManager = myManagerFactory.apply(model);
      when(surface.getSceneManager()).thenReturn(sceneManager);
      when(surface.getSceneManagers()).thenReturn(ImmutableList.of(sceneManager));
      when(surface.getSceneViewAtOrPrimary(anyInt(), anyInt())).thenCallRealMethod();
      when(surface.getFocusedSceneView()).thenReturn(sceneManager.getSceneView());
      if (myDevice != null) {
        model.getConfiguration().setDevice(myDevice, true);
      }
      Scene scene = sceneManager.getScene();
      sceneManager.update();
      when(surface.getScene()).thenReturn(scene);
      when(surface.getProject()).thenReturn(project);
      when(surface.getLayoutType()).thenCallRealMethod();
      when(surface.canZoomToFit()).thenReturn(true);

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

  public static DesignSurface createSurface(Disposable disposableParent,
                                            Class<? extends DesignSurface> surfaceClass,
                                            Function<DesignSurface, InteractionHandler> interactionProviderCreator) {
    DesignSurface surface = mock(surfaceClass);
    Disposer.register(disposableParent, surface);
    List<DesignSurfaceListener> listeners = new ArrayList<>();
    when(surface.getData(any())).thenCallRealMethod();
    when(surface.getLayeredPane()).thenReturn(new JPanel());
    when(surface.getInteractionPane()).thenReturn(new JPanel());
    SelectionModel selectionModel = new DefaultSelectionModel();
    when(surface.getSelectionModel()).thenReturn(selectionModel);
    when(surface.getSize()).thenReturn(new Dimension(1000, 1000));
    when(surface.getScale()).thenReturn(0.5);
    when(surface.getSelectionAsTransferable()).thenCallRealMethod();
    when(surface.getActionManager()).thenReturn(new TestActionManager(surface));
    when(surface.getInteractionManager()).thenReturn(new InteractionManager(surface, interactionProviderCreator.apply(surface)));
    if (surface instanceof NlDesignSurface) {
      when(surface.getAnalyticsManager()).thenReturn(new NlAnalyticsManager(surface));
    }
    doAnswer(inv -> listeners.add(inv.getArgument(0))).when(surface).addListener(any(DesignSurfaceListener.class));
    doAnswer(inv -> listeners.remove((DesignSurfaceListener)inv.getArgument(0))).when(surface).removeListener(any(DesignSurfaceListener.class));
    selectionModel.addListener((model, selection) -> listeners.forEach(listener -> listener.componentSelectionChanged(surface, selection)));
    return surface;
  }

  /**
   * Update the given model to reflect the componentHierarchy in the given builder
   */
  public void updateModel(NlModel model) {
    assertThat(model).isNotNull();
    NlModel newModel = build();
    myModelUpdater.accept(model, newModel);
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
}
