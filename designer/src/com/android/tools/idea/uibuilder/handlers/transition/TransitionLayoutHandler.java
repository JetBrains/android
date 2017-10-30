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
package com.android.tools.idea.uibuilder.handlers.transition;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static com.android.SdkConstants.ATTR_TRANSITION_POSITION;

public class TransitionLayoutHandler extends ConstraintLayoutHandler {

  public static class TransitionLayoutComponentHelper {
    private NlComponent myTransitionLayoutComponent;
    private Method myCallSetTransitionPosition;
    private Method myCallEvaluate;
    private Method myGetMaxTimeMethod;

    public TransitionLayoutComponentHelper(@NotNull NlComponent component) {
      myTransitionLayoutComponent = component;
    }

    private void setTransitionPosition(Object instance, float position) {
      if (myCallSetTransitionPosition == null) {
        try {
          myCallSetTransitionPosition = instance.getClass().getMethod("setTransitionPosition", float.class);
        }
        catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
      }
      if (myCallSetTransitionPosition != null) {
        try {
          myCallSetTransitionPosition.invoke(instance, Float.valueOf(position));
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallSetTransitionPosition = null;
          e.printStackTrace();
        }
      }
    }

    private void evaluate(Object instance) {
      if (myCallEvaluate == null) {
        try {
          myCallEvaluate = instance.getClass().getMethod("evaluate");
        }
        catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
      }
      if (myCallEvaluate != null) {
        try {
          myCallEvaluate.invoke(instance);
        }
        catch (ClassCastException |IllegalAccessException | InvocationTargetException e) {
          myCallEvaluate = null;
          e.printStackTrace();
        }
      }
    }

    public void setValue(float value) {
      ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
      if (info == null) {
        return;
      }
      Object instance = info.getViewObject();
      setTransitionPosition(instance, value);
      evaluate(instance);
      NlModel model = myTransitionLayoutComponent.getModel();
      model.notifyLiveUpdate(false);
    }

    public long getMaxTimeMs() {
      ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
      if (info == null) {
        return 0;
      }

      Object instance = info.getViewObject();
      if (myGetMaxTimeMethod == null) {
        try {
          myGetMaxTimeMethod = instance.getClass().getMethod("getTransitionTimeMs");
        }
        catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
      }

      if (myGetMaxTimeMethod != null) {
        try {
          return (long)myGetMaxTimeMethod.invoke(instance);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          myGetMaxTimeMethod = null;
          e.printStackTrace();
        }
      }
      return 0;
    }
  }

  static class AnimationPositionPanel extends CustomPanel {

    private NlComponent myComponent;
    private TransitionLayoutComponentHelper myTransitionHandler;

    @Override
    public void useComponent(@NotNull NlComponent component) {
      myComponent = component;
      NlComponent transitionLayoutComponent = null;
      if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.TRANSITION_LAYOUT)) {
        transitionLayoutComponent = component;
      } else {
        NlComponent parent = myComponent.getParent();
        if (parent != null && NlComponentHelperKt.isOrHasSuperclass(parent, SdkConstants.TRANSITION_LAYOUT)) {
          transitionLayoutComponent = parent;
        }
      }

      myTransitionHandler = transitionLayoutComponent != null ? new TransitionLayoutComponentHelper(transitionLayoutComponent) : null;
    }

    public AnimationPositionPanel() {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      add(new JLabel("Animation position:"));
      JSlider slider = new JSlider();
      slider.setValue(0);
      slider.setMinimum(0);
      slider.setMaximum(100);
      slider.addChangeListener(e -> {
        if (myTransitionHandler == null) {
          return;
        }
        float value = slider.getValue() / 100f;
        myTransitionHandler.setValue(value);
      });
      add(slider);
    }
  }

  @Override
  @Nullable
  public CustomPanel getCustomPanel() {
    return new AnimationPositionPanel();
  }

  @Override
  @Nullable
  public CustomPanel getLayoutCustomPanel() {
    return new AnimationPositionPanel();
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_TRANSITION_POSITION);
  }

}
