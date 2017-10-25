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
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static com.android.SdkConstants.ATTR_TRANSITION_POSITION;

public class TransitionLayoutHandler extends ConstraintLayoutHandler {

  class AnimationPositionPanel extends CustomPanel {

    private NlComponent myComponent;
    private NlComponent myTransitionLayoutComponent;
    private Method myCallSetTransitionPosition;
    private Method myCallEvaluate;

    @Override
    public void useComponent(@NotNull NlComponent component) {
      myComponent = component;
      myTransitionLayoutComponent = null;
      if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.TRANSITION_LAYOUT)) {
        myTransitionLayoutComponent = component;
      } else {
        NlComponent parent = myComponent.getParent();
        if (parent != null && NlComponentHelperKt.isOrHasSuperclass(parent, SdkConstants.TRANSITION_LAYOUT)) {
          myTransitionLayoutComponent = parent;
        }
      }
    }

    public AnimationPositionPanel() {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      add(new JLabel("Animation position:"));
      JSlider slider = new JSlider();
      slider.setValue(0);
      slider.setMinimum(0);
      slider.setMaximum(100);
      slider.addChangeListener(new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          if (myTransitionLayoutComponent == null) {
            return;
          }
          float value = slider.getValue() / 100f;
          setValue(myTransitionLayoutComponent, value);
        }
      });
      add(slider);
    }

    private void setTransitionPosition(Object instance, float position) {
      if (myCallSetTransitionPosition == null) {
        for (Method m : instance.getClass().getMethods()) {
          if (m.getName().equalsIgnoreCase("setTransitionPosition") && m.getParameterCount() == 1) {
            myCallSetTransitionPosition = m;
            break;
          }
        }
      }
      if (myCallSetTransitionPosition != null) {
        try {
          myCallSetTransitionPosition.invoke(instance, Float.valueOf(position));
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
        catch (InvocationTargetException e) {
          e.printStackTrace();
        }
      }
    }

    private void evaluate(Object instance) {
      if (myCallEvaluate == null) {
        for (Method m : instance.getClass().getMethods()) {
          if (m.getName().equalsIgnoreCase("evaluate") && m.getParameterCount() == 0) {
            myCallEvaluate = m;
            break;
          }
        }
      }
      if (myCallEvaluate != null) {
        try {
          myCallEvaluate.invoke(instance);
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
        catch (InvocationTargetException e) {
          e.printStackTrace();
        }
      }
    }

    public void setValue(@NotNull NlComponent component, float value) {
      assert component != null;

      ViewInfo info = NlComponentHelperKt.getViewInfo(component);
      if (info == null) {
        return;
      }
      Object instance = info.getViewObject();
      setTransitionPosition(instance, value);
      evaluate(instance);
      NlModel model = component.getModel();
      model.notifyLiveUpdate(false);
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
