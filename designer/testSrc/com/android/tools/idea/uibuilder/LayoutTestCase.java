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
package com.android.tools.idea.uibuilder;

import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.rendering.RenderResult;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

public abstract class LayoutTestCase extends AndroidTestCase {

  public LayoutTestCase() {
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    RenderTestUtil.beforeRenderTestCase();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static String getTestDataPath() {
    return getDesignerPluginHome() + "/testData";
  }

  public static String getDesignerPluginHome() {
    return AndroidTestBase.getModulePath("designer");
  }

  @NotNull
  protected ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root) {
    return model(SdkConstants.FD_RES_LAYOUT, name, root);
  }

  @NotNull
  protected ModelBuilder model(@NotNull String resourceFolder, @NotNull String name, @NotNull ComponentDescriptor root) {
    return NlModelBuilderUtil.model(myFacet, myFixture, resourceFolder, name, root);
  }

  protected ComponentDescriptor component(@NotNull String tag) {
    return new ComponentDescriptor(tag);
  }

  protected ScreenFixture screen(@NotNull SyncNlModel model) {
    return new ScreenFixture(model);
  }

  // Format the XML using AndroidStudio formatting
  protected void format(@NotNull XmlFile xmlFile) {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      CodeStyleManager.getInstance(getProject()).reformat(xmlFile);
    });
  }

  @NotNull
  protected ViewEditor editor(ScreenView screenView) {
    ViewEditor editor = Mockito.mock(ViewEditor.class);
    NlModel model = screenView.getSceneManager().getModel();
    when(editor.getModel()).thenReturn(model);
    Scene scene = screenView.getScene();
    when(editor.getScene()).thenReturn(scene);
    when(editor.dpToPx(Mockito.anyInt())).thenAnswer(i -> Coordinates.dpToPx(screenView, (Integer)i.getArguments()[0]));
    when(editor.pxToDp(Mockito.anyInt())).thenAnswer(i -> Coordinates.pxToDp(screenView, (Integer)i.getArguments()[0]));

    return editor;
  }

  @NotNull
  protected RenderResult getRenderResultWithRootViews(ImmutableList<ViewInfo> rootViews) {
    RenderResult result = Mockito.mock(RenderResult.class);
    when(result.getRootViews()).thenReturn(rootViews);
    return result;
  }
}
