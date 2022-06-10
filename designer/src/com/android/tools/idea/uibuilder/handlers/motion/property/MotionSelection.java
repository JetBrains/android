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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.AndroidXConstants.MOTION_LAYOUT;

import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.NlComponentTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
  private final MTag[] myConstraintSetTags;
  private final List<? extends NlComponent> myComponents;

  public MotionSelection(@NotNull MotionEditorSelector.Type type,
                         @NotNull MTag[] tags,
                         @NotNull List<? extends NlComponent> components) {
    myType = type;
    myTags = tags;
    myConstraintSetTags = computeConstraintSetTags(type, tags);
    myComponents = components;
  }

  @Nullable
  private static MTag[] computeConstraintSetTags(@NotNull MotionEditorSelector.Type type, @NotNull MTag[] tags) {
    if (type != MotionEditorSelector.Type.CONSTRAINT) {
      return null;
    }
    MTag[] constraintSets = new MTag[tags.length];
    for (int index = 0; index < tags.length; index++) {
      MTag tag = tags[index];
      MTag constraintSet = null;
      if (tag instanceof NlComponentTag) {
        MotionAttributes attributes = MotionSceneUtils.getAttributes(((NlComponentTag)tag).getComponent());
        if (attributes != null) {
          constraintSet = attributes.getConstraintSet();
        }
      }
      else {
        constraintSet = tag.getParent();
      }
      constraintSets[index] = constraintSet;
    }
    return constraintSets;
  }

  public boolean sameSelection(@NotNull MotionSelection other) {
    return myType == other.myType &&
           myComponents.equals(other.myComponents) &&
           isSameTreeIdHierarchy(myTags, other.myTags) &&
           isSameTreeIdHierarchy(myConstraintSetTags, other.myConstraintSetTags);
  }

  private static boolean isSameTreeIdHierarchy(@Nullable MTag[] tags, @Nullable MTag[] otherTags) {
    if (tags == null || otherTags == null) {
      return tags == null && otherTags == null;
    }
    if (tags.length != otherTags.length) {
      return false;
    }
    if (Arrays.equals(tags, otherTags)) {
      return true;
    }
    for (int index = 0; index < tags.length; index++) {
      if (!isSameTreeIdHierarchy(tags[index], otherTags[index])) {
        return false;
      }
    }
    return true;
  }

  private static boolean isSameTreeIdHierarchy(@Nullable MTag tag, @Nullable MTag other) {
    if (tag == null || other == null) {
      return tag == null && other == null;
    }
    if (tag == other) {
      return true;
    }
    return tag.isSameTreeIdHierarchy(other);
  }

  public void update(@NotNull MotionSelection newSelection) {
    System.arraycopy(newSelection.myTags, 0, myTags, 0, myTags.length);
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
  public NlComponent getComponent() {
    NlComponentTag componentTag = getNlComponentTag();
    if (componentTag != null) {
      return componentTag.getComponent();
    }

    MotionSceneTag motionTag = getMotionSceneTag();
    if (motionTag == null || !Objects.equals(motionTag.getTagName(), MotionSceneAttrs.Tags.CONSTRAINT)) {
      return null;
    }
    return getComponentFromConstraintTag(motionTag);
  }

  /**
   * Find the {@link NlComponent} that correspond to the <code><CustomAttribute></code> being created.
   *
   * Only the following tags can have a <code><CustomAttribute></code>:
   * <ul>
   *   <li><code><Constraint></code></li>
   *   <li><code><KeyAttribute></code></li>
   *   <li><code><KeyCycle></code></li>
   *   <li><code><KeyTimeCycle></code></li>
   * </ul>
   */
  @Nullable
  public NlComponent getComponentForCustomAttributeCompletions() {
    NlComponentTag componentTag = getNlComponentTag();
    if (componentTag != null) {
      return componentTag.getComponent();
    }

    MotionSceneTag motionTag = getMotionSceneTag();
    if (motionTag == null || motionTag.getTagName() == null) {
      return null;
    }
    switch (motionTag.getTagName()) {
      case MotionSceneAttrs.Tags.CONSTRAINT:
        return getComponentFromConstraintTag(motionTag);

      case MotionSceneAttrs.Tags.KEY_ATTRIBUTE:
      case MotionSceneAttrs.Tags.KEY_CYCLE:
      case MotionSceneAttrs.Tags.KEY_TIME_CYCLE:
        return getComponentFromKeyWithArbitraryBackup(motionTag);

      default:
        return null;
    }
  }

  /**
   * Return the {@link NlComponent} matching the id of the <code><Constraint></code> tag.
   */
  @Nullable
  private NlComponent getComponentFromConstraintTag(@NotNull MTag constraintTag) {
    String constraintId = parseId(constraintTag.getAttributeValue(ATTR_ID));
    if (constraintId == null) {
      return null;
    }
    return getViewById(constraintId);
  }

  @Nullable
  private NlComponent getViewById(@NotNull String id) {
    NlComponent motionLayout = getMotionLayoutComponent();
    if (motionLayout == null) {
      return null;
    }
    return motionLayout.getChildren().stream().filter(view -> id.equals(view.getId())).findFirst().orElse(null);
  }

  @Nullable
  private NlComponent getMotionLayoutComponent() {
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
    return motionLayout;
  }

  /**
   * Return the {@link NlComponent} that correspond to this key frame tag.
   */
  private NlComponent getComponentFromKeyWithArbitraryBackup(@NotNull MotionSceneTag keyTag) {
    if (myComponents.isEmpty()) {
      return null;
    }
    NlComponent component = getComponentFromKey(keyTag);
    // If no component is found, just return an arbitrary component (for completions in new custom field dialog).
    return component != null ? component : myComponents.get(0);
  }

  /**
   * Find the {@link NlComponent} that this KeyFrame is working on.
   *
   * Lookup the component from the motionTarget attribute.
   * The motionTarget can be either a view id or a regular expression.
   */
  private NlComponent getComponentFromKey(@NotNull MotionSceneTag keyTag) {
    String motionTarget = keyTag.getAttributeValue(MotionSceneAttrs.Key.MOTION_TARGET);
    if (motionTarget == null) {
      return null;
    }
    String target = parseId(motionTarget);
    if (target != null) {
      // The motionTarget was a view id:
      return getViewById(target);
    }
    // The motionTarget must be a regular expression...
    NlComponent motionLayout = getMotionLayoutComponent();
    if (motionLayout == null) {
      return null;
    }
    return motionLayout.getChildren().stream()
      .filter(component -> matches(component, motionTarget))
      .findFirst().orElse(null);
  }

  private static boolean matches(@NotNull NlComponent component, @NotNull String motionTarget) {
    String tag = component.getAttribute(AUTO_URI, MotionSceneAttrs.LAYOUT_CONSTRAINT_TAG);
    return tag != null && tag.matches(motionTarget);
  }

  @Nullable
  private static String parseId(@Nullable String value) {
    if (value == null) {
      return null;
    }
    ResourceUrl url = ResourceUrl.parse(value);
    if (url == null || url.type != ResourceType.ID || url.name.isEmpty()) {
      return null;
    }
    return url.name;
  }
}
