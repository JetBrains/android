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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MotionLayoutComponentHelper {
  private NlComponent myTransitionLayoutComponent;
  private Method myCallSetTransitionPosition;
  private Method myGetMaxTimeMethod;
  private Method mySetKeyframePositionMethod;
  private Method motionLayoutAccess;
  private Method mySetAttributesMethod;

  public static final int PATH_PERCENT = 0;
  public static final int PATH_PERPENDICULAR = 1;
  public static final int HORIZONTAL_PATH_X = 2;
  public static final int HORIZONTAL_PATH_Y = 3;
  public static final int VERTICAL_PATH_X = 4;
  public static final int VERTICAL_PATH_Y = 5;

  public MotionLayoutComponentHelper(@NotNull NlComponent component) {
    myTransitionLayoutComponent = component;
  }

  public void setAttributes(int dpiValue, String constraintSetId, Object view, Object attributes) {
    ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
    if (info == null) {
      return;
    }
    Object instance = info.getViewObject();
    if (mySetAttributesMethod == null) {
      try {
        mySetAttributesMethod = instance.getClass().getMethod("setAttributes",
                                                             int.class, String.class, Object.class, Object.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }
    if (mySetAttributesMethod != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            mySetAttributesMethod.invoke(instance, dpiValue, constraintSetId, view, attributes);
          }
          catch (Exception e) {
            mySetAttributesMethod = null;
            e.printStackTrace();
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  boolean setKeyframePosition(Object view, int position, int type, float x, float y) {
    ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
    if (info == null) {
      return false;
    }
    Object instance = info.getViewObject();
    if (mySetKeyframePositionMethod == null) {
      try {
        mySetKeyframePositionMethod = instance.getClass().getMethod("setKeyFramePosition",
                                                                    Object.class, int.class, int.class, float.class, float.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }
    final boolean[] didUpdate = {false};
    if (mySetKeyframePositionMethod != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            didUpdate[0] = (boolean)mySetKeyframePositionMethod.invoke(instance, view, Integer.valueOf(position),
                                                                       Integer.valueOf(type), Float.valueOf(x), Float.valueOf(y));
            NlModel model = myTransitionLayoutComponent.getModel();
            model.notifyLiveUpdate(false);
          }
          catch (Exception e) {
            mySetKeyframePositionMethod = null;
            e.printStackTrace();
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    return didUpdate[0];
  }

  private void setTransitionPosition(Object instance, float position) {
    try {
      myCallSetTransitionPosition = instance.getClass().getMethod("setToolPosition", float.class);
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
      myCallSetTransitionPosition = null;
    }
    if (myCallSetTransitionPosition == null) {
      try {
        myCallSetTransitionPosition = instance.getClass().getMethod("setToolPosition", float.class);
        NlModel model = myTransitionLayoutComponent.getModel();
        model.notifyLiveUpdate(false);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }
    if (myCallSetTransitionPosition != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            myCallSetTransitionPosition.invoke(instance, Float.valueOf(position));
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallSetTransitionPosition = null;
            e.printStackTrace();
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public boolean setProgress(float value) {
    ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
    if (info == null) {
      return false;
    }
    Object instance = info.getViewObject();
    try {
      setTransitionPosition(instance, value);
    }
    catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    NlModel model = myTransitionLayoutComponent.getModel();
    model.notifyLiveUpdate(false);
    return true;
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
        return RenderService.runRenderAction(() -> {
          try {
            return (long)myGetMaxTimeMethod.invoke(instance);
          }
          catch (IllegalAccessException | InvocationTargetException e) {
            myGetMaxTimeMethod = null;
            e.printStackTrace();
          }

          return 0;
        }).longValue();
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    return 0;
  }

  public int motionLayoutAccess(int cmd, String type, Object view, float[] in, int inLength, float[] out, int outLength) {
    ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
    if (info == null) {
      return -1;
    }
    Object instance = info.getViewObject();
    if (motionLayoutAccess == null) {
      try {
        motionLayoutAccess = instance.getClass().getMethod("designAccess", int.class, String.class, Object.class, float[].class, int.class,
                                                           float[].class, int.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
        return -1;
      }
    }
    try {
      return (int)motionLayoutAccess.invoke(instance, cmd, type, view, in, inLength, out, outLength);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      myGetMaxTimeMethod = null;
      e.printStackTrace();
    }
    return -1;
  }
}
