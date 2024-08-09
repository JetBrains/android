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
package com.android.tools.idea.common.editor;

import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import java.lang.reflect.Field;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.jetbrains.annotations.NotNull;

public class ActionsToolbarTest extends LayoutTestCase {

  public void testPresentationFactoryCacheDoesNotGrowOnUpdate() throws Exception {
    // Regression test for b/79110899
    ActionsToolbar toolbar = createToolbar();
    ActionToolbarImpl centerToolBar = toolbar.getCenterToolbar();
    Map<?, ?> cache = getPresentationCache(centerToolBar);
    PlatformTestUtil.waitForFuture(centerToolBar.updateActionsAsync());
    int initialSize = cache.size();

    toolbar.updateActions();
    PlatformTestUtil.waitForFuture(centerToolBar.updateActionsAsync());
    assertThat(cache.size()).isAtMost(initialSize);

    toolbar.updateActions();
    PlatformTestUtil.waitForFuture(centerToolBar.updateActionsAsync());
    assertThat(cache.size()).isAtMost(initialSize);

    toolbar.updateActions();
    PlatformTestUtil.waitForFuture(centerToolBar.updateActionsAsync());
    assertThat(cache.size()).isAtMost(initialSize);

    toolbar.updateActions();
    PlatformTestUtil.waitForFuture(centerToolBar.updateActionsAsync());
    assertThat(cache.size()).isAtMost(initialSize);
  }

  public void testNorthAndNorthEastToolbarBackgroundsMatchParentBackground() throws Exception {
    // Regression test for b/346941702
    ActionsToolbar toolbar = createToolbar();
    ActionToolbar northToolbar = toolbar.getNorthToolbar();
    ActionToolbar northEastToolbar = toolbar.getNorthToolbar();
    assertNotNull(northToolbar);
    assertNotNull(northEastToolbar);
    JComponent parentComponent = toolbar.getToolbarComponent();
    JComponent northToolbarComponent = northToolbar.getComponent();
    JComponent northEastToolbarComponent = northEastToolbar.getComponent();

    assertEquals(parentComponent.getBackground().getRGB(), northToolbarComponent.getBackground().getRGB());
    assertEquals(parentComponent.getBackground().getRGB(), northEastToolbarComponent.getBackground().getRGB());

    EdtTestUtil.runInEdtAndWait(() -> UIManager.setLookAndFeel(new DarculaLaf()));

    assertEquals(parentComponent.getBackground().getRGB(), northToolbarComponent.getBackground().getRGB());
    assertEquals(parentComponent.getBackground().getRGB(), northEastToolbarComponent.getBackground().getRGB());
  }

  private ActionsToolbar createToolbar() {
    SyncNlModel model = createModel().build();
    NlDesignSurface surface = (NlDesignSurface)model.getSurface();
    NlActionManager actionManager = new NlActionManager(surface);
    IssueModel issueModel = new IssueModel(myFixture.getTestRootDisposable(), myFixture.getProject());
    doReturn(actionManager).when(surface).getActionManager();
    doReturn(LayoutFileType.INSTANCE).when(surface).getLayoutType();
    when(surface.getIssueModel()).thenReturn(issueModel);
    return new ActionsToolbar(getTestRootDisposable(), surface);
  }

  // Lookup some private fields via reflections.
  // The aim is to test that the action to presentation cache doesn't grow indefinitely.
  private static Map<?,?> getPresentationCache(@NotNull ActionToolbarImpl actionToolbar) throws Exception {
    Field factoryField = ActionToolbarImpl.class.getDeclaredField("myPresentationFactory");
    factoryField.setAccessible(true);
    PresentationFactory factory = (PresentationFactory)factoryField.get(actionToolbar);
    Field cacheField = PresentationFactory.class.getDeclaredField("myPresentations");
    cacheField.setAccessible(true);
    return (Map<?,?>)cacheField.get(factory);
  }

  @NotNull
  private ModelBuilder createModel() {
    return model("linear.xml",
                 component(LINEAR_LAYOUT)
                   .withBounds(0, 0, 2000, 2000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(200, 200, 200, 200)
                       .id("@id/myText")
                       .width("100dp")
                       .height("100dp")
                       .withAttribute("android:layout_x", "100dp")
                       .withAttribute("android:layout_y", "100dp")
                   ));
  }
}
