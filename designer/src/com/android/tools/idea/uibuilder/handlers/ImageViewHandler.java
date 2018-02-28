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
package com.android.tools.idea.uibuilder.handlers;

import com.android.resources.ResourceType;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.flags.StudioFlags.NELE_WIDGET_ASSISTANT;

/**
 * Handler for the {@code <ImageView>} widget
 */
public class ImageViewHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
      ATTR_CONTENT_DESCRIPTION,
      ATTR_BACKGROUND,
      ATTR_SCALE_TYPE,
      ATTR_ADJUST_VIEW_BOUNDS,
      ATTR_CROP_TO_PADDING);
  }

  @Override
  @NotNull
  public String getPreferredProperty() {
    return ATTR_SRC;
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_SRC, getSampleImageSrc())
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tagName)
      .toString();
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      return showImageChooser(editor, newChild);
    }

    // Fallback if dismissed or during previews etc
    if (insertType.isCreate()) {
      setSrcAttribute(newChild, getSampleImageSrc());
    }

    return true;
  }

  private boolean showImageChooser(@NotNull ViewEditor editor, @NotNull NlComponent component) {
    String src = editor.displayResourceInput(EnumSet.of(ResourceType.DRAWABLE));
    if (src != null) {
      setSrcAttribute(component, src);
      return true;
    }

    // Remove the view; the insertion was canceled
    return false;
  }

  /**
   * Returns a source attribute value which points to a sample image. This is typically
   * used to provide an initial image shown on ImageButtons, etc. There is no guarantee
   * that the source pointed to by this method actually exists.
   *
   * @return a source attribute to use for sample images, never null
   */
  @NotNull
  public String getSampleImageSrc() {
    // Builtin graphics available since v1:
    return "@android:drawable/btn_star"; //$NON-NLS-1$
  }

  public void setSrcAttribute(@NotNull NlComponent component, @NotNull String imageSource) {
    NlWriteCommandAction.run(component, "", () -> {
      if (shouldUseSrcCompat(component.getModel())) {
        component.setAttribute(ANDROID_URI, ATTR_SRC, null);
        component.setAttribute(AUTO_URI, ATTR_SRC_COMPAT, imageSource);
      }
      else {
        component.setAttribute(ANDROID_URI, ATTR_SRC, imageSource);
        component.setAttribute(AUTO_URI, ATTR_SRC_COMPAT, null);
      }
    });
  }

  @Nullable
  public String getSrcAttribute(@NotNull NlComponent component) {
    String srcAttribute = null;
    if (shouldUseSrcCompat(component.getModel())) {
      srcAttribute = component.getAttribute(AUTO_URI, ATTR_SRC_COMPAT);
    }

    return srcAttribute != null ? srcAttribute : component.getAttribute(ANDROID_URI, ATTR_SRC);
  }

  public static boolean shouldUseSrcCompat(@NotNull NlModel model) {
    return NlModelHelperKt.moduleDependsOnAppCompat(model) &&
           NlModelHelperKt.currentActivityIsDerivedFromAppCompatActivity(model);
  }

  @Nullable
  @Override
  public ComponentAssistantFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (!NELE_WIDGET_ASSISTANT.get()) {
      return null;
    }

    SceneView sceneView = surface.getCurrentSceneView();
    if (sceneView == null || getSrcAttribute(component) != null) {
      return null;
    }

    JButton button = new JButton("Set image");
    button.addActionListener(e -> showImageChooser(new ViewEditorImpl(surface.getCurrentSceneView()), component));

    return (context) -> button;
  }
}
