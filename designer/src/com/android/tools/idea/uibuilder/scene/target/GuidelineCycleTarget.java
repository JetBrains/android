/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.target;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Implements the guideline cycle
 */
public class GuidelineCycleTarget extends ConstraintTarget {

  private final boolean myIsHorizontal;
  private int mySize = 16;

  public GuidelineCycleTarget(boolean isHorizontal) {
    myIsHorizontal = isHorizontal;
  }

  @Override
  public int getPreferenceLevel() {
    return 0;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform, int l, int t, int r, int b) {
    if (myIsHorizontal) {
      myLeft = l - mySize;
      myTop = t - mySize / 2;
      myRight = l;
      myBottom = t + mySize / 2;
    } else {
      myLeft = l - mySize / 2;
      myTop = t - mySize;
      myRight = l + mySize / 2;
      myBottom = t;
    }
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneTransform) {
    list.addRect(sceneTransform, myLeft, myTop, myRight, myBottom, Color.CYAN);
  }

  @Override
  public void mouseDown(int x, int y) {

  }

  @Override
  public void mouseDrag(int x, int y, @Nullable Target closestTarget) {

  }

  @Override
  public void mouseRelease(int x, int y, @Nullable Target closestTarget) {
    AttributesTransaction attributes = myComponent.getNlComponent().startAttributeTransaction();
    String begin = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN);
    String end = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END);
    String percent = attributes.getAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT);
    SceneComponent parent = myComponent.getParent();
    int value = myComponent.getDrawY() - parent.getDrawY();
    int dimension = parent.getDrawHeight();
    if (!myIsHorizontal) {
      value = myComponent.getDrawX() - parent.getDrawX();
      dimension = parent.getDrawWidth();
    }
    if (begin != null) {
      setEnd(attributes, dimension - value);
    } else if (end != null) {
      setPercent(attributes, value / (float) dimension);
    } else if (percent != null) {
      setBegin(attributes, value);
    }

    attributes.apply();

    NlModel nlModel = myComponent.getNlComponent().getModel();
    Project project = nlModel.getProject();
    XmlFile file = nlModel.getFile();

    String label = "Cycle Guideline";
    WriteCommandAction action = new WriteCommandAction(project, label, file) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        attributes.commit();
      }
    };
    action.execute();
  }

  private void setBegin(AttributesTransaction transaction, int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, position);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, null);
  }

  private void setEnd(AttributesTransaction transaction, int value) {
    String position = String.format(SdkConstants.VALUE_N_DP, value);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, position);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, null);
  }

  private void setPercent(AttributesTransaction transaction, float value) {
    String position = String.valueOf(value);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_BEGIN, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_END, null);
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.LAYOUT_CONSTRAINT_GUIDE_PERCENT, position);
  }
}
