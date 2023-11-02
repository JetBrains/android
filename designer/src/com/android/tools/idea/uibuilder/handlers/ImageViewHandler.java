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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ADJUST_VIEW_BOUNDS;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION;
import static com.android.SdkConstants.ATTR_CROP_TO_PADDING;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_SCALE_TYPE;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_SRC_COMPAT;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.SAMPLE_PREFIX;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.android.SdkConstants.TOOLS_SAMPLE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_WRAP_CONTENT;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ComponentAssistantViewAction;
import com.android.tools.idea.res.PredefinedSampleDataResourceRepository;
import com.android.tools.idea.res.SampleDataResourceItem;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.actions.PickDrawableViewAction;
import com.android.tools.idea.uibuilder.handlers.actions.ScaleTypesViewActionMenu;
import com.android.tools.idea.uibuilder.handlers.assistant.ImageViewAssistant;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.xml.XmlBuilder;
import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handler for the {@code <ImageView>} widget
 */
public class ImageViewHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
      TOOLS_NS_NAME_PREFIX + ATTR_SRC,
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
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      String src = getSrcAttribute(newChild);
      if (src != null && !src.equals(getSampleImageSrc())) {
        setSrcAttribute(newChild, src);
        return true;
      }
      return showImageChooser(newChild);
    }
    return true;
  }

  private boolean showImageChooser(@NotNull NlComponent component) {
    String src = ViewEditor.displayResourceInput(component.getModel(), EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP), true);
    if (src != null) {
      // If the selected item was a sample data item, set in the tools attributes src and not in the regular attribute
      if (src.startsWith(SAMPLE_PREFIX) || src.startsWith(TOOLS_SAMPLE_PREFIX )) {
        setToolsSrc(component, src);
        setSrcAttribute(component, null);
      }
      else {
        setSrcAttribute(component, src);
      }
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
    return TOOLS_SAMPLE_PREFIX + "avatars"; //$NON-NLS-1$
  }

  private static String getSrcAttribute(@NotNull NlComponent newChild) {
    String src = newChild.getAttribute(ANDROID_URI, ATTR_SRC);
    if (src == null) {
      src = newChild.getAttribute(AUTO_URI, ATTR_SRC_COMPAT);
    }
    return src;
  }

  public void setSrcAttribute(@NotNull NlComponent component, @Nullable String imageSource) {
    NlWriteCommandActionUtil.run(component, "", () -> {
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

  public void setToolsSrc(@NotNull NlComponent component, @Nullable SampleDataResourceItem item, int resourceValueIndex) {
    if (item != null) {
      String suffix = resourceValueIndex >= 0 ? "[" + resourceValueIndex + "]" : "";
      setToolsSrc(component, getResourcePrefix(item.getNamespace()) + item.getName() + suffix);
    }
    else {
      setToolsSrc(component, null);
    }
  }

  public void setToolsSrc(@NotNull NlComponent component, @Nullable String value) {
    String attr = shouldUseSrcCompat(component.getModel()) ? ATTR_SRC_COMPAT : ATTR_SRC;
    NlWriteCommandActionUtil.run(component, "Set sample source", () -> component.setAttribute(TOOLS_URI, attr, value));
  }

  @Nullable
  public String getToolsSrc(@NotNull NlComponent component) {
    String attr = shouldUseSrcCompat(component.getModel()) ? ATTR_SRC_COMPAT : ATTR_SRC;
    return component.getAttribute(TOOLS_URI, attr);
  }

  @NotNull
  private static String getResourcePrefix(ResourceNamespace namespace) {
    String prefix;
    if (PredefinedSampleDataResourceRepository.NAMESPACE.equals(namespace)) {
      prefix = TOOLS_SAMPLE_PREFIX;
    }
    else if (ResourceNamespace.TODO().equals(namespace)) {
      prefix = SAMPLE_PREFIX;
    }
    else {
      String packageName = namespace.getPackageName();
      if (packageName != null) {
        prefix = "@" + packageName + ":" + SAMPLE_PREFIX.substring(1);
      }
      else {
        prefix = SAMPLE_PREFIX;
      }
    }
    return prefix;
  }

  public static boolean shouldUseSrcCompat(@NotNull NlModel model) {
    return DependencyManagementUtil.dependsOnAppCompat(model.getModule()) &&
           NlModelHelperKt.currentActivityIsDerivedFromAppCompatActivity(model);
  }

  @Nullable
  private ComponentAssistantFactory getComponentAssistant(@NotNull NlComponent component) {
    return (context) -> new ImageViewAssistant(context, this).getComponent();
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    boolean cacheable = super.addPopupMenuActions(component, actions);

    actions.add(new ComponentAssistantViewAction(this::getComponentAssistant));

    return cacheable;
  }

  @Override
  public List<ViewAction> getPropertyActions(@NotNull List<NlComponent> components) {
    return ImmutableList.of(new PickDrawableViewAction(ANDROID_URI, ATTR_SRC),
                            new ScaleTypesViewActionMenu(ANDROID_URI, ATTR_SCALE_TYPE));
  }
}
