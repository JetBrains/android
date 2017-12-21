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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.RenderTestUtil;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.fixtures.ScreenFixture;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.File;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

public abstract class LayoutTestCase extends AndroidTestCase {

  public LayoutTestCase() {
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
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
    // Now that the Android plugin is kept in a separate place, we need to look in
    // a relative position instead
    String adtPath = PathManager.getHomePath() + "/../adt/idea/designer";
    if (new File(adtPath).exists()) {
      return adtPath;
    }
    return AndroidTestBase.getAndroidPluginHome();
  }

  protected ModelBuilder model(@NotNull String name, @NotNull ComponentDescriptor root) {
    return new ModelBuilder(myFacet, myFixture, name, root,
                            model -> {
                              LayoutlibSceneManager.updateHierarchy(buildViewInfos(model, root), model);
                              SyncLayoutlibSceneManager manager = new SyncLayoutlibSceneManager(model);
                              return manager;
                            },
                            (model, newModel) ->
                              LayoutlibSceneManager
                                .updateHierarchy(AndroidPsiUtils.getRootTagSafely(newModel.getFile()), buildViewInfos(newModel, root),
                                                 model),
                            "layout", NlDesignSurface.class);
  }

  private static List<ViewInfo> buildViewInfos(@NotNull NlModel model, @NotNull ComponentDescriptor root) {
    List<ViewInfo> infos = Lists.newArrayList();
    XmlFile file = model.getFile();
    assertThat(file).isNotNull();
    assertThat(file.getRootTag()).isNotNull();
    infos.add(root.createViewInfo(null, file.getRootTag()));
    return infos;
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
    NlModel model = screenView.getModel();
    when(editor.getModel()).thenReturn(model);
    when(editor.dpToPx(ArgumentMatchers.anyInt())).thenAnswer(i -> Coordinates.dpToPx(screenView, (Integer)i.getArguments()[0]));
    when(editor.pxToDp(ArgumentMatchers.anyInt())).thenAnswer(i -> Coordinates.pxToDp(screenView, (Integer)i.getArguments()[0]));

    return editor;
  }
}
