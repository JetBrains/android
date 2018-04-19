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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlComponentDelegate;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.parsers.AttributeSnapshot;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MotionLayoutComponentDelegate implements NlComponentDelegate {

  private final MotionLayoutTimelinePanel myPanel;

  private static List<String> ourInterceptedAttributes = Arrays.asList(
    SdkConstants.ATTR_LAYOUT_WIDTH,
    SdkConstants.ATTR_LAYOUT_HEIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN,
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_BOTTOM,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_START,
    SdkConstants.ATTR_LAYOUT_GONE_MARGIN_END,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
    SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
    SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
    SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF,
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE,
    SdkConstants.ATTR_LAYOUT_VERTICAL_CHAIN_STYLE,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_WEIGHT,
    SdkConstants.ATTR_LAYOUT_VERTICAL_WEIGHT
  );

  public MotionLayoutComponentDelegate(@NotNull MotionLayoutTimelinePanel panel) {
    myPanel = panel;
  }

  @Override
  public boolean handlesAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      return false;
    }
    if (ourInterceptedAttributes.contains(attribute)) {
      return true;
    }
    return false;
  }

  @Override
  public boolean handlesAttributes(NlComponent component) {
    return false;
  }

  @Override
  public boolean handlesApply(ComponentModification modification) {
    return false;
  }

  @Override
  public boolean handlesCommit(ComponentModification modification) {
    return true;
  }

  @Override
  public String getAttribute(@NotNull NlComponent component, @Nullable String namespace, @NotNull String attribute) {
    switch (myPanel.getCurrentState()) {
      case TL_START: {
        XmlFile file = myPanel.getTransitionFile(component);
        if (file == null) {
          return null;
        }
        XmlTag constraintSet = myPanel.getConstraintSet(file, "@+id/start");
        if (constraintSet == null) {
          return null;
        }
        XmlTag constrainedView = myPanel.getConstrainView(constraintSet, component.getId());
        if (constrainedView == null) {
          return null;
        }
        return constrainedView.getAttributeValue(attribute, namespace);
      }
      case TL_END: {
        XmlFile file = myPanel.getTransitionFile(component);
        if (file == null) {
          return null;
        }
        XmlTag constraintSet = myPanel.getConstraintSet(file, "@+id/end");
        if (constraintSet == null) {
          return null;
        }
        XmlTag constrainedView = myPanel.getConstrainView(constraintSet, component.getId());
        if (constrainedView == null) {
          return null;
        }
        return constrainedView.getAttributeValue(attribute, namespace);
      }
      default:
        // Quick hack to show fixed sizes while we are in the transition
        if (attribute.equals(SdkConstants.ATTR_LAYOUT_WIDTH)
          || attribute.equals(SdkConstants.ATTR_LAYOUT_HEIGHT)) {
          return SdkConstants.VALUE_WRAP_CONTENT;
        }
    }
    return null;
  }

  @Override
  public List<AttributeSnapshot> getAttributes(NlComponent component) {
    return null;
  }

  @Override
  public void apply(ComponentModification modification) {

  }

  @Override
  public void commit(ComponentModification modification) {
    NlComponent component = modification.getComponent();
    Project project = component.getModel().getProject();
    String constraintSetId = null;
    switch (myPanel.getCurrentState()) {
      case TL_START: {
        constraintSetId = "@+id/start";
      } break;
      case TL_END: {
        constraintSetId = "@+id/end";
      } break;
      default:
        return;
    }

    XmlFile file = myPanel.getTransitionFile(component);
    if (file == null) {
      return;
    }

    String finalConstraintSetId = constraintSetId;
    new WriteCommandAction(project, "Set In Transition", file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        XmlTag constraintSet = myPanel.getConstraintSet(file, finalConstraintSetId);
        if (constraintSet == null) {
          return;
        }
        XmlTag constrainedView = myPanel.getConstrainView(constraintSet, component.getId());
        if (constrainedView == null) {
          return;
        }
        modification.commitTo(constrainedView);
      }
    }.execute();

    // A bit heavy handed, but that's what LayoutLib needs...
    LayoutPullParsers.saveFileIfNecessary(file);

    // Let's warn we edited the model.
    NlModel model = component.getModel();
    model.notifyModified(NlModel.ChangeType.EDIT);
  }
}
