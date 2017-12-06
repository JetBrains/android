/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.utils.Pair;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

import static com.android.SdkConstants.*;

/**
 * Keep references in a constraints tag up to date
 */
class ConstraintReferenceManagement {

  /**
   * Make sure that the component is present in the constraints
   *
   * @param component
   * @param constraints
   */
  private static void ensurePresence(@NotNull NlComponent component, @NotNull NlComponent constraints) {
    if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)
        || CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS.isEquals(component.getTagName())
        || NlComponentHelperKt.isOrHasSuperclass(component, CLASS_CONSTRAINT_LAYOUT_REFERENCE)
        || CLASS_CONSTRAINT_LAYOUT_REFERENCE.isEquals(component.getTagName())) {
      return;
    }
    if (exists(component, constraints)) {
      return;
    }

    boolean useAndroidx = NlComponentHelperKt.isOrHasAndroidxSuperclass(component);
    // the component wasn't found, let's add it.

    component.ensureId();
    ApplicationManager.getApplication().runWriteAction(
      () -> {
        XmlTag parentTag = constraints.getTag();
        XmlTag childTag = parentTag
          .createChildTag(useAndroidx ? CLASS_CONSTRAINT_LAYOUT_REFERENCE.newName() : CLASS_CONSTRAINT_LAYOUT_REFERENCE.oldName(), null,
                          null, false);
        childTag.setAttribute(PREFIX_ANDROID + ATTR_ID, NEW_ID_PREFIX + component.getId());
        for (Pair<String, String> pair : ConstraintComponentUtilities.ourLayoutAttributes) {
          String value = component.getLiveAttribute(pair.getFirst(), pair.getSecond());
          String prefix = ConstraintComponentUtilities.ourLayoutUriToPrefix.get(pair.getFirst());
          childTag.setAttribute(prefix + pair.getSecond(), value);
        }
        NlModel model = constraints.getModel();
        NlComponent c = model.createComponent(childTag);
        model.addTags(Arrays.asList(c), constraints, null, InsertType.CREATE);
      }
    );
  }

  /**
   * Returns true if we find a child of the parent that matches
   * the component's id
   *
   * @param component
   * @param parent
   * @return true if id matches
   */
  private static boolean exists(NlComponent component, NlComponent parent) {
    for (NlComponent child : parent.getChildren()) {
      String reference = child.getLiveAttribute(ANDROID_URI, ATTR_ID);
      reference = NlComponent.extractId(reference);
      if (reference != null && reference.equals(component.getId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Initial population of the constraints tag
   *
   * @param constraints the constraints tag we added
   */
  public static void populateConstraints(@NotNull NlComponent constraints) {
    NlComponent parent = constraints.getParent();
    if (parent == null) {
      return;
    }
    String attribute = parent.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_CONSTRAINTSET);
    attribute = NlComponent.extractId(attribute);
    if (attribute == null) {
      return;
    }
    for (NlComponent child : parent.getChildren()) {
      if (NlComponentHelperKt.isOrHasSuperclass(child, CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
        continue;
      }
      ensurePresence(child, constraints);
    }
  }

  /**
   * If the constraintlayout element
   */
  public static void updateConstraints(@NotNull NlComponent primary, @NotNull Scene scene) {
    SceneComponent component = scene.getSceneComponent(primary);
    if (component == null) {
      return;
    }
    SceneComponent parent = component.getParent();
    if (parent == null) {
      return;
    }
    NlComponent nlComponent = parent.getNlComponent();
    String attribute = nlComponent.getLiveAttribute(SHERPA_URI, ATTR_LAYOUT_CONSTRAINTSET);
    attribute = NlComponent.extractId(attribute);
    if (attribute == null) {
      return;
    }
    // else, let's get the component indicated
    NlComponent constraints = null;
    for (SceneComponent child : parent.getChildren()) {
      String childId = child.getNlComponent().getId();
      if (childId != null && childId.equals(attribute)) {
        constraints = child.getNlComponent();
        break;
      }
    }
    if (constraints == null) {
      return;
    }
    for (NlComponent child : nlComponent.getChildren()) {
      if (NlComponentHelperKt.isOrHasSuperclass(child, CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS)) {
        continue;
      }
      ensurePresence(child, constraints);
    }

    // Let's remove any superfluous child
    ArrayList<NlComponent> toRemove = null;
    for (NlComponent child : constraints.getChildren()) {
      if (exists(child, nlComponent)) {
        continue;
      }
      // we should remove it
      if (toRemove == null) {
        toRemove = new ArrayList<>();
      }
      toRemove.add(child);
    }
    if (toRemove == null) {
      return;
    }
    constraints.getModel().delete(toRemove);
  }
}
