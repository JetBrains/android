/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.actions.PasteWithIdOptionAction;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.ui.resourcemanager.model.ResourceDataManagerKt;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.util.MockCopyPasteManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RuleChain;
import com.intellij.testFramework.RunsInEdt;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@RunsInEdt
public class NlDesignSurfaceActionHandlerTest {
  private final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk();

  @Rule
  public RuleChain chain = new RuleChain(myProjectRule, new EdtRule());

  private NlDesignSurface mySurface;
  private Disposable myDisposable;
  private SyncNlModel myModel;
  private NlComponent myButton;
  private NlComponent myTextView;
  private DesignSurfaceActionHandler mySurfaceActionHandler;

  private CopyPasteManager myCopyPasteManager;

  private final DataContext context = DataContext.EMPTY_CONTEXT;

  @Before
  public void setUp() throws Exception {
    myModel = createModel();
    // If using a lambda, it can be reused by the JVM and causing a Exception because the Disposable is already disposed.
    myDisposable = Disposer.newDisposable();
    mySurface = NlDesignSurface.builder(myProjectRule.getProject(), myDisposable, (surface, model) -> {
        SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel((SyncNlModel)model);
        manager.setIgnoreRenderRequests(true);
        return manager;
      })
    .build();
    PlatformTestUtil.waitForFuture(mySurface.addModelWithoutRender(myModel));
    myCopyPasteManager = new MockCopyPasteManager();
    mySurfaceActionHandler = new NlDesignSurfaceActionHandler(mySurface, myCopyPasteManager);

    myButton = findFirst(BUTTON);
    myTextView = findFirst(TEXT_VIEW);
  }

  @After
  public void tearDown() throws Exception {
    Disposer.dispose(myModel);
    Disposer.dispose(myDisposable);
    myButton = null;
    myTextView = null;
    mySurfaceActionHandler = null;
  }

  @Test
  public void testCopyIsNotAvailableWhenNothingIsSelected() {
    assertThat(mySurfaceActionHandler.isCopyVisible(context)).isTrue();
    assertThat(mySurfaceActionHandler.isCopyEnabled(context)).isFalse();
    mySurfaceActionHandler.performCopy(context);
  }

  @Test
  public void testCopyIsWhenNothingIsSelected() {
    // Check that there are no exceptions creating a handler:
    new NlDesignSurfaceActionHandler(mySurface).performCopy(context);
  }

  @Test
  public void testCopyMultiple() {
    mySurface.getSelectionModel().toggle(myTextView);
    mySurface.getSelectionModel().toggle(myButton);
    assertThat(mySurfaceActionHandler.isCopyVisible(context)).isTrue();
    assertThat(mySurfaceActionHandler.isCopyEnabled(context)).isTrue();
    assertThat(myCopyPasteManager.getContents()).isNull();
    mySurfaceActionHandler.performCopy(context);
    assertThat(myCopyPasteManager.getContents()).isNotNull();
  }

  @Test
  public void testCopyWithOneComponentSelected() {
    mySurface.getSelectionModel().toggle(myTextView);
    assertThat(mySurfaceActionHandler.isCopyVisible(context)).isTrue();
    assertThat(mySurfaceActionHandler.isCopyEnabled(context)).isTrue();
    assertThat(myCopyPasteManager.getContents()).isNull();
    mySurfaceActionHandler.performCopy(context);
    assertThat(myCopyPasteManager.getContents()).isNotNull();
  }

  @Test
  public void testPasteWillChangeSelectionToPastedComponent() {
    // Need to use the real copyPasteManager for checking the result of selection model.
    mySurfaceActionHandler = new NlDesignSurfaceActionHandler(mySurface);

    assertThat(myModel.getTreeReader().getComponents().get(0).getChildCount()).isEqualTo(3);

    mySurface.getSelectionModel().toggle(myTextView);
    assertThat(mySurface.getSelectionModel().getSelection().size()).isEqualTo(1);
    assertThat(mySurface.getSelectionModel().getSelection().get(0)).isSameAs(myTextView);

    mySurfaceActionHandler.performCopy(context);
    mySurfaceActionHandler.performPaste(context);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    assertThat(myModel.getTreeReader().getComponents().get(0).getChildCount()).isEqualTo(4);
    assertThat(mySurface.getSelectionModel().getSelection().size()).isEqualTo(1);
    // Paste will put the item before the original one
    assertThat(myModel.getTreeReader().getComponents().get(0).getChild(1)).isSameAs(myTextView);
    assertThat(myModel.getTreeReader().getComponents().get(0).getChild(2)).isSameAs(mySurface.getSelectionModel().getSelection().get(0));
    assertThat(myModel.getTreeReader().getComponents().get(0).getChild(2)).isNotSameAs(myTextView);
  }

  @Test
  public void testPasteResourceUrl() {
    Transferable content = new Transferable() {

      @Override
      public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{ResourceDataManagerKt.RESOURCE_URL_FLAVOR};
      }

      @Override
      public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor == ResourceDataManagerKt.RESOURCE_URL_FLAVOR;
      }

      @NotNull
      @Override
      public Object getTransferData(DataFlavor flavor) {
        return ResourceUrl.create("namespace", ResourceType.DRAWABLE, "name");
      }
    };
    myCopyPasteManager.setContents(content);
    mySurfaceActionHandler.performPaste(context);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(findFirst("ImageView")).isNotNull();
  }

  @Test
  public void testPasteGenerateNewIdsWithContext() {
    DataContext newContext = new DataContext() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
        return (PasteWithIdOptionAction.getPASTE_WITH_NEW_IDS_KEY().is(dataId)) ? true : null;
      }
    };
    copyTextViewAndPaste(newContext);
    List<NlComponent> textComponents = findAll(TEXT_VIEW);
    assertThat(textComponents).hasSize(2);
    assertThat(textComponents.stream().map(NlComponent::getId).toList()).containsExactly("myText", "myText2");
  }

  @Test
  public void testPasteUsingOldIdsWithContext() {
    DataContext newContext = new DataContext() {
      @Override
      public @Nullable Object getData(@NotNull String dataId) {
        return (PasteWithIdOptionAction.getPASTE_WITH_NEW_IDS_KEY().is(dataId)) ? false : null;
      }
    };
    copyTextViewAndPaste(newContext);
    List<NlComponent> textComponents = findAll(TEXT_VIEW);
    assertThat(textComponents).hasSize(2);
    assertThat(textComponents.stream().map(NlComponent::getId).toList()).containsExactly("myText", "myText");
  }

  private void copyTextViewAndPaste(@NotNull DataContext context) {
    mySurface.getSelectionModel().toggle(myTextView);
    mySurfaceActionHandler.performCopy(context);
    mySurface.getSelectionModel().toggle(myTextView);
    mySurface.getSelectionModel().toggle(findFirst(LINEAR_LAYOUT));
    mySurfaceActionHandler.performPaste(context);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = NlModelBuilderUtil.model(myProjectRule, "layout", "relative.xml",
                                                    new ComponentDescriptor(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     new ComponentDescriptor(LINEAR_LAYOUT)
                                       .withBounds(0, 0, 200, 200)
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .children(
                                         new ComponentDescriptor(BUTTON)
                                           .withBounds(0, 0, 100, 100)
                                           .id("@+id/myButton")
                                           .width("100dp")
                                           .height("100dp")),
                                     new ComponentDescriptor(TEXT_VIEW)
                                       .withBounds(0, 200, 100, 100)
                                       .id("@+id/myText")
                                       .width("100dp")
                                       .height("100dp"),
                                     new ComponentDescriptor(ABSOLUTE_LAYOUT)
                                       .withBounds(0, 300, 400, 500)
                                       .width("400dp")
                                       .height("500dp")));
    final SyncNlModel model = builder.build();
    assertThat(model.getTreeReader().getComponents().size()).isEqualTo(1);
    assertThat(NlTreeDumper.dumpTree(model.getTreeReader().getComponents())).isEqualTo("""
          NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}
              NlComponent{tag=<LinearLayout>, bounds=[0,0:200x200}
                  NlComponent{tag=<Button>, bounds=[0,0:100x100}
              NlComponent{tag=<TextView>, bounds=[0,200:100x100}
              NlComponent{tag=<AbsoluteLayout>, bounds=[0,300:400x500}""");
    return model;
  }

  @NotNull
  private NlComponent findFirst(@NotNull String tagName) {
    Optional<NlComponent> first = findAll(tagName).stream().findFirst();
    assertThat(first.isPresent()).isTrue();
    return first.get();
  }

  private List<NlComponent> findAll(@NotNull String tagName) {
    return myModel.getTreeReader().getComponents().stream()
      .flatMap(NlComponent::flatten)
      .filter(component -> component.getTagName().equals(tagName))
      .toList();
  }
}
