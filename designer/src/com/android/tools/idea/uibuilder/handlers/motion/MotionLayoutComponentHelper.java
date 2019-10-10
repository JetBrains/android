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
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.utils.Pair;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;

public class MotionLayoutComponentHelper {

  private Method myCallSetTransitionPosition;
  private Method myCallSetState;
  private Method myCallGetState;
  private Method myCallGetStartState;
  private Method myCallGetEndState;
  private Method myCallDisableAutoTransition;
  private Method myCallGetProgress;
  private Method myCallSetTransition;
  private Method myGetMaxTimeMethod;
  private Method mySetKeyframePositionMethod;
  private Method motionLayoutAccess;
  private Method mySetAttributesMethod;
  private Method myGetKeyframeMethod;
  private Method myGetKeyFramePositionsMethod;
  private Method mySetKeyframeMethod;
  private Method myGetPositionKeyframeMethod;
  private Method myGetKeyframeAtLocationMethod;
  private Method myCallIsInTransition;
  private Method myUpdateLiveAttributesMethod;
  private Method myGetAnimationPathMethod;

  public static final int PATH_PERCENT = 0;
  public static final int PATH_PERPENDICULAR = 1;
  public static final int HORIZONTAL_PATH_X = 2;
  public static final int HORIZONTAL_PATH_Y = 3;
  public static final int VERTICAL_PATH_X = 4;
  public static final int VERTICAL_PATH_Y = 5;

  private final Object myDesignTool;
  private final NlComponent myMotionLayoutComponent;
  private final boolean DEBUG = false;
  private static boolean mShowPaths = true;

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
          if (DEBUG) {
            e.printStackTrace();
          }
        }
      }
    }
    catch (NoSuchMethodException e) {
      if (DEBUG) {
        e.printStackTrace();
      }
    }
    myMotionLayoutComponent = component;
    myDesignTool = designInstance;
  }

  public int getPath(NlComponent nlComponent, final float[] path, int size) {

    if (myDesignTool == null) {
      return -1;
    }
    if (myGetAnimationPathMethod == null) {
      try {

        Method[] methods = myDesignTool.getClass().getMethods();
        myGetAnimationPathMethod = myDesignTool.getClass().getMethod("getAnimationPath",
                                                                     Object.class, float[].class, int.class);
      }
      catch (NoSuchMethodException e) {
        e.printStackTrace();
      }
    }

    if (myGetAnimationPathMethod != null) {
      try {

        return (Integer)RenderService.runRenderAction(() -> {
          try {
            ViewInfo info = NlComponentHelperKt.getViewInfo(nlComponent);
            if (info == null) {
              return -1;
            }
            return myGetAnimationPathMethod.invoke(myDesignTool, info.getViewObject(), path, size);
          }
          catch (Exception e) {

            myGetAnimationPathMethod = null;
            e.printStackTrace();
          }
          return -1;
        });
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    return 0;
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return null;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return false;
        }) == Boolean.TRUE;
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return null;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
      disableAutoTransition(true);
      try {
        myCallSetTransition = myDesignTool.getClass().getMethod("setTransition", String.class, String.class);
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    if (myCallSetState == null) {
      return;
    }
    NlModel model = myMotionLayoutComponent.getModel();
    model.notifyLiveUpdate(false);
  }

  public void disableAutoTransition(boolean disable) {
    if (myDesignTool == null) {
      return;
    }
    if (myCallDisableAutoTransition == null) {
      try {

        myCallDisableAutoTransition = myDesignTool.getClass().getMethod("disableAutoTransition", boolean.class);
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        myCallDisableAutoTransition = null;
        return;
      }
    }
    if (myCallDisableAutoTransition != null) {
      try {
        RenderService.runRenderAction(() -> {
          try {
            myCallDisableAutoTransition.invoke(myDesignTool, disable);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallDisableAutoTransition = null;
            if (DEBUG) {
              e.printStackTrace();
            }
          }
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
  }

  public String getState() {
    String state = null;
    if (myDesignTool == null) {
      return state;
    }
    if (myCallGetState == null) {
      try {
        myCallGetState = myDesignTool.getClass().getMethod("getState");
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        myCallGetState = null;
        return state;
      }
    }
    if (myCallGetState != null) {
      try {
        state = RenderService.runRenderAction(() -> {
          try {
            return (String)myCallGetState.invoke(myDesignTool);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallSetState = null;
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return null;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    return state;
  }

  public String getStartState() {
    String state = null;
    if (myDesignTool == null) {
      return state;
    }
    if (myCallGetStartState == null) {
      try {
        myCallGetStartState = myDesignTool.getClass().getMethod("getStartState");
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        myCallGetStartState = null;
        return state;
      }
    }
    if (myCallGetStartState != null) {
      try {
        state = RenderService.runRenderAction(() -> {
          try {
            return (String)myCallGetStartState.invoke(myDesignTool);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallGetStartState = null;
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return null;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    return state;
  }

  public String getEndState() {
    String state = null;
    if (myDesignTool == null) {
      return state;
    }
    if (myCallGetEndState == null) {
      try {
        myCallGetEndState = myDesignTool.getClass().getMethod("getEndState");
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        myCallGetEndState = null;
        return state;
      }
    }
    if (myCallGetEndState != null) {
      try {
        state = RenderService.runRenderAction(() -> {
          try {
            return (String)myCallGetEndState.invoke(myDesignTool);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallGetEndState = null;
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return null;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    return state;
  }

  public float getProgress() {
    float progress = 0;
    if (myDesignTool == null) {
      return progress;
    }
    if (myCallGetProgress == null) {
      try {
        myCallGetProgress = myDesignTool.getClass().getMethod("getProgress");
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        myCallGetProgress = null;
        return progress;
      }
    }
    if (myCallGetProgress != null) {
      try {
        progress = RenderService.runRenderAction(() -> {
          try {
            return (Float)myCallGetProgress.invoke(myDesignTool);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallGetProgress = null;
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return 0f;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    return progress;
  }

  public boolean isInTransition() {
    boolean isInTransition = false;
    if (myDesignTool == null) {
      return isInTransition;
    }
    if (myCallIsInTransition == null) {
      try {
        myCallIsInTransition = myDesignTool.getClass().getMethod("isInTransition");
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        myCallIsInTransition = null;
        return isInTransition;
      }
    }
    if (myCallIsInTransition != null) {
      try {
        isInTransition = RenderService.runRenderAction(() -> {
          try {
            return (Boolean)myCallIsInTransition.invoke(myDesignTool);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallIsInTransition = null;
            if (DEBUG) {
              e.printStackTrace();
            }
          }
          return false;
        });
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
      }
    }
    return isInTransition;
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
        if (DEBUG) {
          e.printStackTrace();
        }
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
            if (DEBUG) {
              e.printStackTrace();
            }
          }

          return 0;
        }).longValue();
      }
      catch (Exception e) {
        if (DEBUG) {
          e.printStackTrace();
        }
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
        motionLayoutAccess =
          myDesignTool.getClass().getMethod("designAccess", int.class, String.class, Object.class, float[].class, int.class,
                                            float[].class, int.class);
      }
      catch (NoSuchMethodException e) {
        if (DEBUG) {
          e.printStackTrace();
        }
        return -1;
      }
    }
    try {
      return (int)motionLayoutAccess.invoke(myDesignTool, cmd, type, view, in, inLength, out, outLength);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      myGetMaxTimeMethod = null;
      if (DEBUG) {
        e.printStackTrace();
      }
    }
    return -1;
  }

  /**
   * Make sure we have usable Ids, even if only temporary
   *
   * @param component
   */
  private void updateIds(@NotNull NlComponent component) {
    ResourceIdManager manager = ResourceIdManager.get(component.getModel().getModule());
    updateId(manager, component);
    if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      for (NlComponent child : component.getChildren()) {
        updateId(manager, child);
      }
    }
    component = component.getParent();
    if (component != null && NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.CLASS_MOTION_LAYOUT)) {
      for (NlComponent child : component.getChildren()) {
        updateId(manager, child);
      }
    }
  }

  private void updateId(@NotNull ResourceIdManager manager, @NotNull NlComponent component) {
    String id = component.getId();
    ResourceReference reference = new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, id);
    Integer resolved = manager.getCompiledId(reference);
    if (resolved == null) {
      resolved = manager.getOrGenerateId(reference);
      if (resolved != null) {
        ViewInfo view = NlComponentHelperKt.getViewInfo(component);
        if (view != null && view.getViewObject() != null) {
          android.view.View androidView = (android.view.View)view.getViewObject();
          androidView.setId(resolved.intValue());
        }
      }
    }
  }

  public void updateLiveAttributes(NlComponent component, ComponentModification modification, String state) {
    final Configuration configuration = modification.getComponent().getModel().getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    ResourceIdManager manager = ResourceIdManager.get(modification.getComponent().getModel().getModule());
    ViewInfo info = NlComponentHelperKt.getViewInfo(modification.getComponent());

    if (info == null || (info != null && info.getViewObject() == null)) {
      return;
    }

    HashMap<String, String> attributes = new HashMap<>();
    for (Pair<String, String> key : modification.getAttributes().keySet()) {
      String value = modification.getAttributes().get(key);
      if (value != null) {
        if (value.startsWith("@id/") || value.startsWith("@+id/")) {
          value = value.substring(value.indexOf('/') + 1);
          Integer resolved = manager.getCompiledId(new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, value));
          if (resolved == null) {
            updateIds(modification.getComponent());
            resolved = manager.getOrGenerateId(new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, value));
          }
          if (resolved != null) {
            value = resolved.toString();
          }
        }
        else if (value.equalsIgnoreCase("parent")) {
          value = "0";
        }
      }
      attributes.put(key.getSecond(), value);
    }

    setAttributes(dpiValue, state, info.getViewObject(), attributes);
  }

  public int getKeyframePos(NlComponent component, int[] type, float[] pos) {
    if (myDesignTool == null) {
      return -1;
    }
    ViewInfo info = NlComponentHelperKt.getViewInfo(component);

    if (info == null || (info != null && info.getViewObject() == null)) {
      return -1;
    }

    if (myGetKeyFramePositionsMethod == null) {
      try {
        myGetKeyFramePositionsMethod = myDesignTool.getClass().getMethod("getKeyFramePositions",
                                                                         Object.class, int[].class, float[].class);
      }
      catch (NoSuchMethodException e) {
        if (true) {
          e.printStackTrace();
        }
      }
    }

    if (myGetKeyFramePositionsMethod != null) {
      try {
        return RenderService.runRenderAction(() -> {
          try {
            return (Integer)myGetKeyFramePositionsMethod.invoke(myDesignTool, info.getViewObject(), type, pos);
          }
          catch (Exception e) {
            myGetKeyFramePositionsMethod = null;
            if (true) {
              e.printStackTrace();
            }
          }
          return null;
        });
      }
      catch (Exception e) {
        if (true) {
          e.printStackTrace();
        }
      }
    }

    return -1;
  }

  public void setShowPaths(boolean show) {
    mShowPaths = show;
  }

  public boolean getShowPaths() {
    return mShowPaths;
  }
}
