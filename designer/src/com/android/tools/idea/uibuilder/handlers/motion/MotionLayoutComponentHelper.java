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

  private Method myCallSetTransitionPosition;
  private Method myCallSetState;
  private Method myCallSetTransition;
  private Method myGetMaxTimeMethod;
  private Method mySetKeyframePositionMethod;
  private Method motionLayoutAccess;
  private Method mySetAttributesMethod;
  private Method myGetKeyframeMethod;
  private Method mySetKeyframeMethod;
  private Method myGetPositionKeyframeMethod;
  private Method myGetKeyframeAtLocationMethod;

  public static final int PATH_PERCENT = 0;
  public static final int PATH_PERPENDICULAR = 1;
  public static final int HORIZONTAL_PATH_X = 2;
  public static final int HORIZONTAL_PATH_Y = 3;
  public static final int VERTICAL_PATH_X = 4;
  public static final int VERTICAL_PATH_Y = 5;

  private final Object myDesignTool;
  private final NlComponent myMotionLayoutComponent;

  public MotionLayoutComponentHelper(@NotNull NlComponent component) {
    ViewInfo info = NlComponentHelperKt.getViewInfo(component);
    if (info == null) {
      myDesignTool = null;
      myMotionLayoutComponent = null;
      return;
    }
    Object instance = info.getViewObject();
    if (instance == null) {
      myDesignTool = null;
      myMotionLayoutComponent = null;
      return;
    }
    Object designInstance = null;
    try {
      Method accessor = instance.getClass().getMethod("getDesignTool");
      if (accessor != null) {
        try {
          designInstance = RenderService.runRenderAction(() -> accessor.invoke(instance));
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    myMotionLayoutComponent = component;
    myDesignTool = designInstance;
  }

  public Object getKeyframeAtLocation(Object view, float x, float y) {
    if (myDesignTool == null) {
      return null;
    }
    if (myGetKeyframeAtLocationMethod == null) {
      try {
        myGetKeyframeAtLocationMethod = myDesignTool.getClass().getMethod("getKeyframeAtLocation",
                                                                      Object.class, float.class, float.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }

    if (myGetKeyframeAtLocationMethod != null) {
      try {
        return RenderService.runRenderAction(() -> {
          try {
            return myGetKeyframeAtLocationMethod.invoke(myDesignTool, view, x, y);
          }
          catch (Exception e) {
            myGetKeyframeAtLocationMethod = null;
            e.printStackTrace();
          }
          return null;
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    return null;
  }

  public boolean getPositionKeyframe(Object keyframe, Object view, float x, float y, String[] attributes, float[] values) {
    if (myDesignTool == null) {
      return false;
    }
    if (myGetPositionKeyframeMethod == null) {
      try {
        myGetPositionKeyframeMethod = myDesignTool.getClass().getMethod("getPositionKeyframe",
                                                            Object.class, Object.class, float.class, float.class,
                                                            String[].class, float[].class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }

    if (myGetPositionKeyframeMethod != null) {
      try {
        return RenderService.runRenderAction(() -> {
          try {
            return myGetPositionKeyframeMethod.invoke(myDesignTool, keyframe, view, x, y, attributes, values);
          }
          catch (Exception e) {
            myGetPositionKeyframeMethod = null;
            e.printStackTrace();
          }
          return false;
        }) == Boolean.TRUE;
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    return false;
  }

  public Object getKeyframe(int type, int target, int position) {
    if (myDesignTool == null) {
      return null;
    }
    if (myGetKeyframeMethod == null) {
      try {
        myGetKeyframeMethod = myDesignTool.getClass().getMethod("getKeyframe",
                                                              int.class, int.class, int.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }

    if (myGetKeyframeMethod != null) {
      try {
        return RenderService.runRenderAction(() -> {
          try {
            return myGetKeyframeMethod.invoke(myDesignTool, type, target, position);
          }
          catch (Exception e) {
            myGetKeyframeMethod = null;
            e.printStackTrace();
          }
          return null;
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    return null;
  }

  public void setKeyframe(Object keyframe, String tag, Object value) {
    if (myDesignTool == null) {
      return;
    }
    if (mySetKeyframeMethod == null) {
      try {
        mySetKeyframeMethod = myDesignTool.getClass().getMethod("setKeyframe",
                                                            Object.class, String.class, Object.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }

    if (mySetKeyframeMethod != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            mySetKeyframeMethod.invoke(myDesignTool, keyframe, tag, value);
          }
          catch (Exception e) {
            mySetKeyframeMethod = null;
            e.printStackTrace();
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public void setAttributes(int dpiValue, String constraintSetId, Object view, Object attributes) {
    if (myDesignTool == null) {
      return;
    }
    if (mySetAttributesMethod == null) {
      try {
        mySetAttributesMethod = myDesignTool.getClass().getMethod("setAttributes",
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
            mySetAttributesMethod.invoke(myDesignTool, dpiValue, constraintSetId, view, attributes);
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
    if (myDesignTool == null) {
      return false;
    }
    if (mySetKeyframePositionMethod == null) {
      try {
        mySetKeyframePositionMethod = myDesignTool.getClass().getMethod("setKeyFramePosition",
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
            didUpdate[0] = (boolean)mySetKeyframePositionMethod.invoke(myDesignTool, view, Integer.valueOf(position),
                                                                       Integer.valueOf(type), Float.valueOf(x), Float.valueOf(y));
            NlModel model = myMotionLayoutComponent.getModel();
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

  private boolean setTransitionPosition(float position) {
    if (myDesignTool == null) {
      return false;
    }
    if (myCallSetTransitionPosition == null) {
      try {
        myCallSetTransitionPosition = myDesignTool.getClass().getMethod("setToolPosition", float.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
        myCallSetTransitionPosition = null;
        return false;
      }
    }
    if (myCallSetTransitionPosition != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            myCallSetTransitionPosition.invoke(myDesignTool, Float.valueOf(position));
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
    if (myCallSetTransitionPosition == null) {
      return false;
    }
    return true;
  }

  public boolean setProgress(float value) {
    if (myDesignTool == null) {
      return false;
    }
    NlModel model = myMotionLayoutComponent.getModel();
    //model.notifyModified(NlModel.ChangeType.EDIT);
    if (!setTransitionPosition(value)) {
      return false;
    }
    model.notifyLiveUpdate(false);
    return true;
  }

  public void setTransition(String start, String end) {
    if (myDesignTool == null) {
      return;
    }
    if (myCallSetTransition == null) {
      try {
        myCallSetTransition = myDesignTool.getClass().getMethod("setTransition", String.class, String.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
        myCallSetTransition = null;
        return;
      }
    }
    if (myCallSetTransition != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            myCallSetTransition.invoke(myDesignTool, start, end);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallSetTransition = null;
            e.printStackTrace();
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (myCallSetTransition == null) {
      return;
    }
    NlModel model = myMotionLayoutComponent.getModel();
    model.notifyLiveUpdate(false);
  }

  public void setState(String state) {
    if (myDesignTool == null) {
      return;
    }
    if (myCallSetState == null) {
      try {
        myCallSetState = myDesignTool.getClass().getMethod("setState", String.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
        myCallSetState = null;
        return;
      }
    }
    if (myCallSetState != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            myCallSetState.invoke(myDesignTool, state);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallSetState = null;
            e.printStackTrace();
          }
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    if (myCallSetState == null) {
      return;
    }
    NlModel model = myMotionLayoutComponent.getModel();
    model.notifyLiveUpdate(false);
  }

  public long getMaxTimeMs() {
    if (myDesignTool == null) {
      return 0;
    }
    if (myGetMaxTimeMethod == null) {
      try {
        myGetMaxTimeMethod = myDesignTool.getClass().getMethod("getTransitionTimeMs");
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }

    if (myGetMaxTimeMethod != null) {
      try {
        return RenderService.runRenderAction(() -> {
          try {
            return (long)myGetMaxTimeMethod.invoke(myDesignTool);
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
    if (myDesignTool == null) {
      return -1;
    }
    if (motionLayoutAccess == null) {
      try {
        motionLayoutAccess = myDesignTool.getClass().getMethod("designAccess", int.class, String.class, Object.class, float[].class, int.class,
                                                           float[].class, int.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
        return -1;
      }
    }
    try {
      return (int)motionLayoutAccess.invoke(myDesignTool, cmd, type, view, in, inLength, out, outLength);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      myGetMaxTimeMethod = null;
      e.printStackTrace();
    }
    return -1;
  }
}
