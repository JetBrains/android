/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.MOTION_LAYOUT;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.NlComponentTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a concept of a selected state for the motion properties panel.
 *
 * A selection consist of a selection type, the selected MTag(s) and the
 * components selected in the UI.
 */
public class MotionSelection {
  private final MotionEditorSelector.Type myType;
  private final MTag[] myTags;
  private final List<? extends NlComponent> myComponents;

  public MotionSelection(@NotNull MotionEditorSelector.Type type,
                         @NotNull MTag[] tags,
                         @NotNull List<? extends NlComponent> components) {
    myType = type;
    myTags = tags;
    myComponents = components;
  }

  @NotNull
  public MotionEditorSelector.Type getType() {
    return myType;
  }

  @NotNull
  public MTag[] getTags() {
    return myTags;
  }

  @NotNull
  public List<? extends NlComponent> getComponents() {
    return myComponents;
  }

  @Nullable
  public MotionSceneTag getMotionSceneTag() {
    if (myTags.length == 0) {
      return null;
    }
    MTag firstTag = myTags[0];
    if (!(firstTag instanceof MotionSceneTag)) {
      return null;
    }
    return (MotionSceneTag)firstTag;
  }

  @Nullable
  private NlComponentTag getNlComponentTag() {
    if (myTags.length == 0) {
      return null;
    }
    MTag firstTag = myTags[0];
    if (!(firstTag instanceof NlComponentTag)) {
      return null;
    }
    return (NlComponentTag)firstTag;
  }

  @Nullable
  public String getMotionSceneTagName() {
    MotionSceneTag tag = getMotionSceneTag();
    return tag != null ? tag.getTagName() : null;
  }

  @Nullable
  public XmlTag getXmlTag(@Nullable MotionSceneTag tag) {
    return tag != null ? tag.getXmlTag() : null;
  }

  @Nullable
  public PsiFile getSceneFile() {
    MotionSceneTag tag = getMotionSceneTag();
    XmlTag xml = getXmlTag(tag);
    if (xml != null) {
      return xml.getContainingFile();
    }
    return null;
  }

  @Nullable
  public MotionAttributes getMotionAttributes() {
    NlComponent component = getComponent();
    return component != null ? MotionSceneUtils.getAttributes(component) : null;
  }

  @Nullable
  public Icon getComponentIcon() {
    NlComponent component = getComponent();
    if (component == null) {
      return null;
    }
    NlComponent.XmlModelComponentMixin mixin = component.getMixin();
    return mixin != null ? mixin.getIcon() : null;
  }

  @Nullable
  public String getComponentId() {
    NlComponent component = getComponent();
    if (component == null) {
      return null;
    }
    return component.getAttribute(ANDROID_URI, ATTR_ID);
  }

  @Nullable
  public String getConstraintSetId() {
    if (myType != MotionEditorSelector.Type.CONSTRAINT) {
      return null;
    }
    MotionSceneTag tag = getMotionSceneTag();
    MTag parent = null;
    if (tag != null) {
      parent = tag.getParent();
    }
    else {
      MotionAttributes attrs = getMotionAttributes();
      if (attrs != null) {
        parent = attrs.getConstraintSet();
      }
    }
    return parent != null ? parent.getAttributeValue(ATTR_ID) : null;
  }

  @Nullable
  private NlComponent getComponent() {
    NlComponentTag componentTag = getNlComponentTag();
    if (componentTag != null) {
      return componentTag.getComponent();
    }

    MotionSceneTag motionTag = getMotionSceneTag();
    if (motionTag == null || !motionTag.getTagName().equals(MotionSceneAttrs.Tags.CONSTRAINT)) {
      return null;
    }
    String constraintId = Utils.stripID(motionTag.getAttributeValue(ATTR_ID));
    if (myComponents.isEmpty()) {
      return null;
    }
    NlComponent motionLayout = myComponents.get(0);
    if (!MOTION_LAYOUT.isEquals(motionLayout.getTagName())) {
      motionLayout = motionLayout.getParent();
    }
    if (motionLayout == null || !MOTION_LAYOUT.isEquals(motionLayout.getTagName())) {
      return null;
    }
    return motionLayout.getChildren().stream().filter(view -> constraintId.equals(view.getId())).findFirst().orElse(null);
  }
}
