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

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.util.MockCopyPasteManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.common.LayoutTestUtilities.createScreen;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotEquals;

public class NlDesignSurfaceActionHandlerTest extends LayoutTestCase {

  private NlDesignSurface mySurface;
  private Disposable myDisposable;
  private SyncNlModel myModel;
  private ScreenView myScreen;
  private NlComponent myButton;
  private NlComponent myTextView;
  private DesignSurfaceActionHandler mySurfaceActionHandler;

  private CopyPasteManager myCopyPasteManager;

  private DataContext context = DataContext.EMPTY_CONTEXT;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = createModel();
    myScreen = createScreen(myModel);
    // If using a lambda, it can be reused by the JVM and causing a Exception because the Disposable is already disposed.
    myDisposable = Disposer.newDisposable();
    mySurface = NlDesignSurface.builder(getProject(), myDisposable)
      .setSceneManagerProvider((surface, model) -> {
        SyncLayoutlibSceneManager manager = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel((SyncNlModel)model);
        manager.setIgnoreRenderRequests(true);
        return manager;
      })
    .build();
    mySurface.setModel(myModel);
    myCopyPasteManager = new MockCopyPasteManager();
    mySurfaceActionHandler = new NlDesignSurfaceActionHandler(mySurface, myCopyPasteManager);

    myButton = findFirst(BUTTON);
    myTextView = findFirst(TEXT_VIEW);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myModel);
      Disposer.dispose(myDisposable);
      myButton = null;
      myTextView = null;
      mySurfaceActionHandler = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testCopyIsNotAvailableWhenNothingIsSelected() {
    assertThat(mySurfaceActionHandler.isCopyVisible(context)).isTrue();
    assertThat(mySurfaceActionHandler.isCopyEnabled(context)).isFalse();
    mySurfaceActionHandler.performCopy(context);
  }

  public void testCopyIsWhenNothingIsSelected() {
    assertNoException(NullPointerException.class, () -> new NlDesignSurfaceActionHandler(mySurface).performCopy(context));
  }

  public void testCopyMultiple() {
    mySurface.getSelectionModel().toggle(myTextView);
    mySurface.getSelectionModel().toggle(myButton);
    assertThat(mySurfaceActionHandler.isCopyVisible(context)).isTrue();
    assertThat(mySurfaceActionHandler.isCopyEnabled(context)).isTrue();
    assertNull(myCopyPasteManager.getContents());
    mySurfaceActionHandler.performCopy(context);
    assertNotNull(myCopyPasteManager.getContents());
  }

  public void testCopyWithOneComponentSelected() {
    mySurface.getSelectionModel().toggle(myTextView);
    assertThat(mySurfaceActionHandler.isCopyVisible(context)).isTrue();
    assertThat(mySurfaceActionHandler.isCopyEnabled(context)).isTrue();
    assertNull(myCopyPasteManager.getContents());
    mySurfaceActionHandler.performCopy(context);
    assertNotNull(myCopyPasteManager.getContents());
  }

  // Disabled because it is flaky: b/157650498
  public void ignore_testPasteWillChangeSelectionToPastedComponent() {
    // Need to use the real copyPasteManager for checking the result of selection model.
    mySurfaceActionHandler = new NlDesignSurfaceActionHandler(mySurface);

    assertEquals(3, myModel.getComponents().get(0).getChildCount());

    mySurface.getSelectionModel().toggle(myTextView);
    assertEquals(1, mySurface.getSelectionModel().getSelection().size());
    assertEquals(myTextView, mySurface.getSelectionModel().getSelection().get(0));

    mySurfaceActionHandler.performCopy(context);
    mySurfaceActionHandler.performPaste(context);

    assertEquals(4, myModel.getComponents().get(0).getChildCount());
    assertEquals(1, mySurface.getSelectionModel().getSelection().size());
    // Paste will put the item before the original one
    assertEquals(myModel.getComponents().get(0).getChild(1), myTextView);
    assertEquals(myModel.getComponents().get(0).getChild(2), mySurface.getSelectionModel().getSelection().get(0));
    assertNotEquals(myModel.getComponents().get(0).getChild(2), myTextView);
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(LINEAR_LAYOUT)
                                       .withBounds(0, 0, 200, 200)
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .children(
                                         component(BUTTON)
                                           .withBounds(0, 0, 100, 100)
                                           .id("@+id/myButton")
                                           .width("100dp")
                                           .height("100dp")),
                                     component(TEXT_VIEW)
                                       .withBounds(0, 200, 100, 100)
                                       .id("@+id/myText")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(ABSOLUTE_LAYOUT)
                                       .withBounds(0, 300, 400, 500)
                                       .width("400dp")
                                       .height("500dp")));
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<LinearLayout>, bounds=[0,0:200x200}\n" +
                 "        NlComponent{tag=<Button>, bounds=[0,0:100x100}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,200:100x100}\n" +
                 "    NlComponent{tag=<AbsoluteLayout>, bounds=[0,300:400x500}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  @NotNull
  private NlComponent findFirst(@NotNull String tagName) {
    NlComponent component = findFirst(tagName, myModel.getComponents());
    assert component != null;
    return component;
  }

  @Nullable
  private static NlComponent findFirst(@NotNull String tagName, @NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getTagName().equals(tagName)) {
        return component;
      }
      NlComponent child = findFirst(tagName, component.getChildren());
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  public void testFoo1() {
    ImmutableList<Integer> l1 = ImmutableList.of(1);
    List<Integer> l2 = new ArrayList<>();
    l2.add(1);
    l2.equals(l1);
  }
}