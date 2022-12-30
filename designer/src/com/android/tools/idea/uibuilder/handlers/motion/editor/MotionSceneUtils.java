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
package com.android.tools.idea.uibuilder.handlers.motion.editor;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;

/**
 * Provide static accessor to various MotionLayout Related functions
 */
public final class MotionSceneUtils {
  private static boolean DEBUG = false;
  public final static String MOTION_LAYOUT_PROPERTIES = "motionLayoutProperties";
  public static final String MTAG_ACCESS = "MotionTagForMotionScene";


  public static MTag getMTag(NlComponent nlComponent) {
    return (MTag)nlComponent.getClientProperty(MTAG_ACCESS);
  }

  public static void setMTag(NlComponent nlComponent, MTag tag) {
    nlComponent.putClientProperty(MTAG_ACCESS, tag);
  }

  public static void putConstraintTag(NlComponent nl, MTag tag) {
    nl.putClientProperty(MTAG_ACCESS, tag);
  }

  public static MotionAttributes getAttributes(NlComponent nlComponent) {
    return (MotionAttributes)nlComponent.getClientProperty(MOTION_LAYOUT_PROPERTIES);
  }

  /**
   * Get the tagWriter for a nl component
   *
   * @param nlComponent
   * @return
   */
  public static MTag.TagWriter getTagWriter(NlComponent nlComponent) {
    MotionAttributes m = (MotionAttributes)nlComponent.getClientProperty(MOTION_LAYOUT_PROPERTIES);

    MTag tag = getMTag(nlComponent);
    if (tag instanceof MTag.TagWriter) {
      return (MTag.TagWriter)tag;
    }

    if (tag == null) {
      MTag parent = m.getConstraintSet();
      MTag.TagWriter writer = parent.getChildTagWriter(MotionSceneAttrs.Tags.CONSTRAINT);
      m.fillTagWriter(writer);
      writer.setAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID, "@+id/" + nlComponent.getId());
      setMTag(nlComponent, writer);
      return writer;
    }
    if (MotionSceneAttrs.Tags.CONSTRAINT.equals(tag.getTagName())) { // we already have  a Constraint
      MTag.TagWriter tw = tag.getTagWriter();
      setMTag(nlComponent, tw);
      tw.addCommitListener(newTag -> {
        setMTag(nlComponent, newTag);
      });
      return tw;
    }
    if (MotionSceneAttrs.Tags.CONSTRAINTSET.equals(tag.getTagName())) {

    }
    return null;
  }

  /**
   * Get access to a {@link MTag.TagWriter}.
   * <p>
   * We may later add some business logic at this level.
   */
  public static MTag.TagWriter getTagWriter(@NotNull MTag tag) {
    return tag.getTagWriter();
  }

  /**
   * Get access to a {@link MTag.TagWriter} for creating a tag.
   * <p>
   * We may later add some business logic at this level.
   */
  public static MTag.TagWriter getChildTagWriter(@NotNull MTag tag, @NotNull String newChildTag) {
    return tag.getChildTagWriter(newChildTag);
  }

  public static boolean isUnderConstraintSet(NlComponent nlComponent) {
    MotionAttributes attributes = getAttributes(nlComponent);
    return (attributes != null);
  }

  public static void deleteRelatedConstraintSets(MTag motionScene, String toRemove) {
    if (toRemove == null) {
      return;
    }
    toRemove = Utils.stripID(toRemove);
    MTag[] sceneTags = motionScene.getChildTags();
    for (MTag tag : sceneTags) {
      String tagName = tag.getTagName();
      switch (tagName) {
        case (MotionSceneAttrs.Tags.CONSTRAINTSET): {
          MTag[] constraint = tag.getChildTags();
          for (MTag mTag : constraint) {
            String id = Utils.stripID(mTag.getAttributeValue(MotionSceneAttrs.ATTR_ANDROID_ID));
            if (toRemove.equals(id)) {
              mTag.getTagWriter().deleteTag().commit("delete");
            }
          }
        }
        break;
        case (MotionSceneAttrs.Tags.TRANSITION): {
          for (MTag keySetTags : tag.getChildTags(MotionSceneAttrs.Tags.KEY_FRAME_SET)) {
            for (MTag keyTags : keySetTags.getChildTags()) {
              String id = keyTags.getAttributeValue(MotionSceneAttrs.Key.MOTION_TARGET);
              if (id.startsWith("@") && toRemove.equals(Utils.stripID(id))) {
                keyTags.getTagWriter().deleteTag().commit("delete");
              }
            }
          }
        }
      }
    }
  }
}
