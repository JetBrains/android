/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.view.View;
import com.android.AndroidXConstants;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.res.StudioResourceIdManager;
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.rendering.RenderService;
import com.android.tools.res.ids.ResourceIdManager;
import com.android.utils.Pair;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.annotations.NotNull;

public class MotionLayoutComponentHelper {

  private final InvokeMethod<Object> myGetKeyframeAtLocation = new InvokeMethod<>("getKeyframeAtLocation", Object.class, float.class, float.class);
  private final InvokeMethod<Object> myGetKeyframe = new InvokeMethod<>("getKeyframe", Object.class, int.class, int.class);

  private Method myCallSetTransitionPosition;
  private Method myCallSetState;
  private Method myCallGetState;
  private Method myCallGetStartState;
  private Method myCallGetEndState;
  private Method myCallDisableAutoTransition;
  private Method myCallGetProgress;
  private Method myCallSetTransition;
  private Method myGetMaxTimeMethod;
  private Method mySetAttributesMethod;
  private Method myGetKeyFramePositionsMethod;
  private Method myGetKeyFrameInfoMethod;
  private Method mySetKeyframeMethod;
  private Method myGetPositionKeyframeMethod;
  private Method myCallIsInTransition;
  private Method myGetAnimationPathMethod;

  private Object myDesignTool;
  private final NlComponent myMotionLayoutComponent;
  private static boolean mShowPaths = true;
  @NotNull
  private final CompletableFuture<Void> myFuture;
  private final HashMap<String, float[]> myCachedPath = new HashMap<>();
  private boolean myCachedPositionKeyframe = false;
  private String myCachedState = null;
  private String myCachedStartState = null;
  private String myCachedEndState = null;
  private float myCachedProgress = 0;
  private boolean myCachedIsInTransition = false;
  /**
   * Limiter to to avoid calling isInTransition too many times.
   */
  @SuppressWarnings("UnstableApiUsage")
  private final RateLimiter myCachedIsInTransitionRateLimiter = RateLimiter.create(20);
  private long myCachedMaxTimeMs = 0L;
  private final HashMap<String, KeyframePos> myCachedKeyframePos = new HashMap<>();
  private final HashMap<String, KeyframeInfo> myCachedKeyframeInfo = new HashMap<>();

  private static final Cache<NlComponent, MotionLayoutComponentHelper> sCache = CacheBuilder.newBuilder()
    .weakValues()
    .weakKeys()
    .build();


  public static void clearCache() {
    sCache.invalidateAll();
  }

  public static MotionLayoutComponentHelper create(@NotNull NlComponent component) {
    try {
      return sCache.get(component, () -> new MotionLayoutComponentHelper(component));
    }
    catch (ExecutionException e) {
      return new MotionLayoutComponentHelper(component);
    }
  }

  private MotionLayoutComponentHelper(@NotNull NlComponent component) {
    component = MotionUtils.getMotionLayoutAncestor(component);
    ViewInfo info = component != null ? NlComponentHelperKt.getViewInfo(component) : null;
    if (info == null) {
      myFuture = CompletableFuture.completedFuture(null);
      myDesignTool = null;
      myMotionLayoutComponent = null;
      return;
    }
    Object instance = info.getViewObject();
    if (instance == null) {
      myFuture = CompletableFuture.completedFuture(null);
      myDesignTool = null;
      myMotionLayoutComponent = null;
      return;
    }
    CompletableFuture<Void> getDesignToolFuture = null;
    try {
      Method accessor = instance.getClass().getMethod("getDesignTool");
      try {
        getDesignToolFuture =
          RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> getDesignInstance(accessor, instance));
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
    catch (NoSuchMethodException e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
    myFuture = getDesignToolFuture != null ? getDesignToolFuture : CompletableFuture.completedFuture(null);
    myMotionLayoutComponent = component;
  }

  private void getDesignInstance(Method accessor, Object instance) {
    try {
      myDesignTool = accessor.invoke(instance);
      myGetKeyframeAtLocation.update(myDesignTool);
      myGetKeyframe.update(myDesignTool);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  public int getPath(NlComponent nlComponent, final float[] path, int size) {
    float[] tmpPath = myCachedPath.get(nlComponent.getId());
    cachedGetPath(nlComponent, path, size);
    if (tmpPath != null) {
      System.arraycopy(tmpPath, 0, path, 0, tmpPath.length);
      return tmpPath.length;
    }
    return -1;
  }

  private void whenDesignToolAvailable(@NotNull Runnable runnable) {
    if (myDesignTool != null) {
      runnable.run();
      return;
    }

    myFuture.thenRunAsync(() -> {
      if (myDesignTool != null) {
        runnable.run();
      }
    }, EdtExecutorService.getInstance());
  }

  private boolean isMyDesignToolNotAvailable() {
    if (myDesignTool == null) {
      try {
        myFuture.get(250, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
      return myDesignTool == null;
    }
    return false;
  }

  private void cachedGetPath(NlComponent nlComponent, final float[] path, int size) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myGetAnimationPathMethod == null) {
      try {
        myGetAnimationPathMethod = myDesignTool.getClass().getMethod("getAnimationPath",
                                                                     Object.class, float[].class, int.class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }

    if (myGetAnimationPathMethod != null) {
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            ViewInfo info = NlComponentHelperKt.getViewInfo(nlComponent);
            if (info == null) {
              return;
            }
            myGetAnimationPathMethod.invoke(myDesignTool, info.getViewObject(), path, size);
            myCachedPath.put(nlComponent.getId(), path);
            return;
          }
          catch (Exception e) {
            myGetAnimationPathMethod = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
  }

  public Object getKeyframe(Object view, int type, int position) {
    return myGetKeyframe.invoke(view, type, position);
  }

  /**
   * Utility class for invoking methods
   * @param <T>
   */
  private static class InvokeMethod<T> {
    String myMethodName;
    Method myMethod = null;
    Object myDesignTool = null;
    Class<?>[] myParameters = null;

    public InvokeMethod(String methodName, Class<?>... parameters) {
      myMethodName = methodName;
      myParameters = ArrayUtil.copyOf(parameters);
    }

    public void update(Object designTool) {
      if (designTool == null) {
        return;
      }
      myDesignTool = designTool;
      try {
        myMethod = designTool.getClass().getMethod(myMethodName, myParameters);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }

    public T invoke(Object... parameters) {
      if (myMethod != null) {
        try {
          return RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
            try {
              //noinspection unchecked
              return (T) myMethod.invoke(myDesignTool, parameters);
            } catch (Exception e) {
              Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
            }
            return null;
          }).get(250, TimeUnit.MILLISECONDS);
        }
        catch (Exception e) {
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      }
      return null;
    }
  }

  public boolean getPositionKeyframe(Object keyframe, Object view, float x, float y, String[] attributes, float[] values) {
    cachedGetPositionKeyframe(keyframe, view, x, y, attributes, values);
    return myCachedPositionKeyframe;
  }

  private void cachedGetPositionKeyframe(Object keyframe, Object view, float x, float y, String[] attributes, float[] values) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myGetPositionKeyframeMethod == null) {
      try {
        myGetPositionKeyframeMethod = myDesignTool.getClass().getMethod("getPositionKeyframe",
                                                                        Object.class, Object.class, float.class, float.class,
                                                                        String[].class, float[].class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }

    if (myGetPositionKeyframeMethod != null) {
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            myCachedPositionKeyframe = myGetPositionKeyframeMethod.invoke(myDesignTool, keyframe, view, x, y, attributes, values) == Boolean.TRUE;
          }
          catch (Exception e) {
            myGetPositionKeyframeMethod = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
  }

  public void setKeyframe(Object keyframe, String tag, Object value) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (mySetKeyframeMethod == null) {
      try {
        mySetKeyframeMethod = myDesignTool.getClass().getMethod("setKeyframe",
                                                                Object.class, String.class, Object.class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }

    if (mySetKeyframeMethod != null) {
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            mySetKeyframeMethod.invoke(myDesignTool, keyframe, tag, value);
          }
          catch (Exception e) {
            mySetKeyframeMethod = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
  }

  public void setAttributes(int dpiValue, String constraintSetId, Object view, Object attributes) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (mySetAttributesMethod == null) {
      try {
        mySetAttributesMethod = myDesignTool.getClass().getMethod("setAttributes",
                                                                  int.class, String.class, Object.class, Object.class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
    if (mySetAttributesMethod != null) {
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            mySetAttributesMethod.invoke(myDesignTool, dpiValue, constraintSetId, view, attributes);
          }
          catch (Exception e) {
            mySetAttributesMethod = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
  }

  private boolean setTransitionPosition(float position) {
    if (myCallSetTransitionPosition == null) {
      try {
        myCallSetTransitionPosition = myDesignTool.getClass().getMethod("setToolPosition", float.class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallSetTransitionPosition = null;
        return false;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCallSetTransitionPosition.invoke(myDesignTool, position);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallSetTransitionPosition = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
    return myCallSetTransitionPosition != null;
  }

  public void setProgress(float value) {
    whenDesignToolAvailable(() -> {
      NlModel model = myMotionLayoutComponent.getModel();

      if (!setTransitionPosition(value)) {
        return;
      }

      model.notifyLiveUpdate();
      refresh(myMotionLayoutComponent);
    });
  }

  public void setTransition(String start, String end) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myCallSetTransition == null) {
      disableAutoTransition(true);
      try {
        myCallSetTransition = myDesignTool.getClass().getMethod("setTransition", String.class, String.class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallSetTransition = null;
        return;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCallSetTransition.invoke(myDesignTool, start, end);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallSetTransition = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
    if (myCallSetTransition == null) {
      return;
    }
    NlModel model = myMotionLayoutComponent.getModel();
    model.notifyLiveUpdate();
  }

  public void setState(String state) {
    whenDesignToolAvailable(() -> {
      if (myCallSetState == null) {
        try {
          myCallSetState = myDesignTool.getClass().getMethod("setState", String.class);
        }
        catch (NoSuchMethodException e) {
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          myCallSetState = null;
          return;
        }
      }
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            myCallSetState.invoke(myDesignTool, state);
          }
          catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
            myCallSetState = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
      if (myCallSetState == null) {
        return;
      }
      NlModel model = myMotionLayoutComponent.getModel();
      model.notifyLiveUpdate();
    });
  }

  public void disableAutoTransition(boolean disable) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myCallDisableAutoTransition == null) {
      try {

        myCallDisableAutoTransition = myDesignTool.getClass().getMethod("disableAutoTransition", boolean.class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallDisableAutoTransition = null;
        return;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCallDisableAutoTransition.invoke(myDesignTool, disable);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallDisableAutoTransition = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  public String getState() {
    cachedGetState();
    return myCachedState;
  }

  private void cachedGetState() {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myCallGetState == null) {
      try {
        myCallGetState = myDesignTool.getClass().getMethod("getState");
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallGetState = null;
        return;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCachedState = (String)myCallGetState.invoke(myDesignTool);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallGetState = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  public String getStartState() {
    cachedGetStartState();
    return myCachedStartState;
  }

  private void cachedGetStartState() {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myCallGetStartState == null) {
      try {
        myCallGetStartState = myDesignTool.getClass().getMethod("getStartState");
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallGetStartState = null;
        return;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCachedStartState = (String)myCallGetStartState.invoke(myDesignTool);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallGetStartState = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  public String getEndState() {
    cachedGetEndState();
    return myCachedEndState;
  }

  private void cachedGetEndState() {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myCallGetEndState == null) {
      try {
        myCallGetEndState = myDesignTool.getClass().getMethod("getEndState");
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallGetEndState = null;
        return;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCachedEndState = (String)myCallGetEndState.invoke(myDesignTool);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallGetEndState = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
        return;
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  public float getProgress() {
    cachedGetProgress();
    return myCachedProgress;
  }

  private void cachedGetProgress() {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    if (myCallGetProgress == null) {
      try {
        myCallGetProgress = myDesignTool.getClass().getMethod("getProgress");
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallGetProgress = null;
        return;
      }
    }
    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCachedProgress = (Float)myCallGetProgress.invoke(myDesignTool);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallGetProgress = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  public boolean isInTransition() {
    cachedIsInTransition();
    return myCachedIsInTransition;
  }

  private void cachedIsInTransition() {
    if (isMyDesignToolNotAvailable()) {
      return;
    }

    if (myCallIsInTransition == null) {
      try {
        myCallIsInTransition = myDesignTool.getClass().getMethod("isInTransition");
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        myCallIsInTransition = null;
        return;
      }
    }

    //noinspection UnstableApiUsage
    if (!myCachedIsInTransitionRateLimiter.tryAcquire()) return;

    try {
      RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
        try {
          myCachedIsInTransition = (Boolean)myCallIsInTransition.invoke(myDesignTool);
        }
        catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
          myCallIsInTransition = null;
          Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
        }
      });
    }
    catch (Exception e) {
      Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
    }
  }

  /**
   * Make sure we have usable Ids, even if only temporary
   */
  private void updateIds(@NotNull NlComponent component) {
    ResourceIdManager manager = StudioResourceIdManager.get(component.getModel().getModule());
    updateId(manager, component);
    if (NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CLASS_MOTION_LAYOUT)) {
      for (NlComponent child : component.getChildren()) {
        updateId(manager, child);
      }
    }
    component = component.getParent();
    if (component != null && NlComponentHelperKt.isOrHasSuperclass(component, AndroidXConstants.CLASS_MOTION_LAYOUT)) {
      for (NlComponent child : component.getChildren()) {
        updateId(manager, child);
      }
    }
  }

  private void updateId(@NotNull ResourceIdManager manager, @NotNull NlComponent component) {
    String id = component.getId();
    if (id == null) return;
    ResourceReference reference = new ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, id);
    Integer resolved = manager.getCompiledId(reference);
    if (resolved == null) {
      resolved = manager.getOrGenerateId(reference);
      ViewInfo view = NlComponentHelperKt.getViewInfo(component);
      if (view != null && view.getViewObject() != null) {
        View androidView = (View)view.getViewObject();
        androidView.setId(resolved);
      }
    }
  }

  public void updateLiveAttributes(ComponentModification modification, String state) {
    final Configuration configuration = modification.getComponent().getModel().getConfiguration();
    final int dpiValue = configuration.getDensity().getDpiValue();
    ResourceIdManager manager = StudioResourceIdManager.get(modification.getComponent().getModel().getModule());
    ViewInfo info = NlComponentHelperKt.getViewInfo(modification.getComponent());

    if (info == null || info.getViewObject() == null) {
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
          value = resolved.toString();
        }
        else if (value.equalsIgnoreCase("parent")) {
          value = "0";
        }
      }
      attributes.put(key.getSecond(), value);
    }

    setAttributes(dpiValue, state, info.getViewObject(), attributes);
  }

  /**
   * Get the KeyFrames for the view controlled by this MotionController.
   * The call is designed to be efficient because it will be called 30x Number of views a second
   *
   * @param component the view to return keyframe positions
   * @param type is position(0-100) + 1000*mType(1=Attributes, 2=Position, 3=TimeCycle 4=Cycle 5=Trigger
   * @param pos the x&y position of the keyFrame along the path
   * @return Number of keyFrames found
   */

  public int getKeyframePos(NlComponent component, int[] type, float[] pos) {
    cachedGetKeyframePos(component, type, pos);
    if (myCachedKeyframePos.containsKey(component.getId())) {
      KeyframePos tmpPos = myCachedKeyframePos.get(component.getId());
      for (int i = 0; i < tmpPos.myLength; i++) {
        type[i] = tmpPos.myType[i];
        pos[i * 2] = tmpPos.myPos[i * 2];
        pos[i * 2 + 1] = tmpPos.myPos[i * 2 + 1];
      }
      return tmpPos.myLength;
    }
    return -1;
  }

  private static class KeyframePos {
    int[] myType;
    float[] myPos;
    int myLength;

    KeyframePos(int[] type, float[] pos, int length) {
      myType = Arrays.copyOf(type, type.length);
      myPos = Arrays.copyOf(pos, pos.length);
      myLength = length;
    }
  }

  private void cachedGetKeyframePos(NlComponent component, int[] type, float[] pos) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    ViewInfo info = NlComponentHelperKt.getViewInfo(component);

    if (info == null || info.getViewObject() == null) {
      return;
    }

    if (myGetKeyFramePositionsMethod == null) {
      try {
        myGetKeyFramePositionsMethod = myDesignTool.getClass().getMethod("getKeyFramePositions",
                                                                         Object.class, int[].class, float[].class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }

    if (myGetKeyFramePositionsMethod != null) {
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            int[] tmpType = Arrays.copyOf(type, type.length);
            float[] tmpPos = Arrays.copyOf(pos, pos.length);
            int framePosition = (Integer)myGetKeyFramePositionsMethod.invoke(myDesignTool, info.getViewObject(), tmpType, tmpPos);
            myCachedKeyframePos.put(component.getId(), new KeyframePos(tmpType, tmpPos, framePosition));
          }
          catch (Exception e) {
            myGetKeyFramePositionsMethod = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        }).get(250, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
  }

  /**
   * A utility class to read MotionLayout KeyFrame info structure
   */
  public static class KeyInfo {
    int[] myInfo;
    int myKeyInfoCount;
    int myOffsetCursor = 0;
    int myCursor = 0;
    @SuppressWarnings("unused")
    public static final int KEY_TYPE_ATTRIBUTES = 1;
    public static final int KEY_TYPE_POSITION = 2;
    @SuppressWarnings("unused")
    public static final int KEY_TYPE_TIME_CYCLE = 3;
    @SuppressWarnings("unused")
    public static final int KEY_TYPE_CYCLE = 4;
    @SuppressWarnings("unused")
    public static final int KEY_TYPE_TRIGGER = 5;
    private final static int OFF_TYPE = 1;
    private final static int OFF_FRAME_POS = 2;
    private final static int OFF_LOC_X = 3;
    private final static int OFF_LOC_Y = 4;
    private final static int OFF_KEY_POS_TYPE = 5;
    private final static int OFF_KEY_POS_PERCENT_X = 6;
    private final static int OFF_KEY_POS_PERCENT_Y = 7;

    public int getIndex() {
      return myCursor;
    }

    /**
     * Set the info int array on this object so that it can be parsed
     */
    public void setInfo(int[] info, int count) {
      myOffsetCursor = 0;
      myKeyInfoCount = count;
      myInfo = info;
      reset();
    }

    /**
     * reset the counters so that they can be used with next()
     * in a while(next()) {... } reset();
     */
    public void reset() {
      myCursor = -1;
      myOffsetCursor = 0;
    }

    public boolean next() {
      myCursor++;
      if (myCursor > 0) {
        int len = myInfo[myOffsetCursor];
        myOffsetCursor += len ;
      }
      return myCursor < myKeyInfoCount;
    }

    public int getType() {
      return myInfo[myOffsetCursor + OFF_TYPE];
    }

    public int getFramePosition() {
      return myInfo[myOffsetCursor + OFF_FRAME_POS];
    }

    public float getLocationX() {
      return Float.intBitsToFloat(myInfo[myOffsetCursor + OFF_LOC_X]);
    }

    public float getLocationY() {
      return Float.intBitsToFloat(myInfo[myOffsetCursor + OFF_LOC_Y]);
    }

    public int getKeyPosType() {
      return myInfo[myOffsetCursor + OFF_KEY_POS_TYPE];
    }

    public float getKeyPosPercentX() {
      return Float.intBitsToFloat(myInfo[myOffsetCursor + OFF_KEY_POS_PERCENT_X]);
    }

    public float getKeyPosPercentY() {
      return Float.intBitsToFloat(myInfo[myOffsetCursor + OFF_KEY_POS_PERCENT_Y]);
    }
  }

  /**
   * Get the KeyFrames for the view controlled by this MotionController.
   * The call is designed to be efficient because it will be called 30x Number of views a second
   *
   * @param component the view to return keyframe positions
   * @param type      type of keyframes your are interested in  1=Attributes, 2=Position, 3=TimeCycle 4=Cycle 5=Trigger, -1 == all
   * @param keyInfo   the x&y position of the keyFrame along the path
   * @return Number of keyFrames found
   */

  public int getKeyframeInfo(NlComponent component, int type, int[] keyInfo) {
    cachedGetKeyframeInfo(component, type, keyInfo);
    if (myCachedKeyframeInfo.containsKey(component.getId())) {
      KeyframeInfo tmpInfo = myCachedKeyframeInfo.get(component.getId());
      System.arraycopy(tmpInfo.myKeyInfo, 0, keyInfo, 0, tmpInfo.myKeyInfo.length);
      return tmpInfo.myNoOfKeyPosition;
    }
    return -1;
  }

  private static class KeyframeInfo {
    int[] myKeyInfo;
    int myNoOfKeyPosition;

    KeyframeInfo(int[] keyInfo, int noOfKeyPosition) {
      myKeyInfo = keyInfo;
      myNoOfKeyPosition = noOfKeyPosition;
    }
  }

  private void cachedGetKeyframeInfo(NlComponent component, int type, int[] keyInfo) {
    if (isMyDesignToolNotAvailable()) {
      return;
    }
    ViewInfo info = NlComponentHelperKt.getViewInfo(component);

    if (info == null || info.getViewObject() == null) {
      return;
    }

    if (myGetKeyFrameInfoMethod == null) {
      try {
        myGetKeyFrameInfoMethod = myDesignTool.getClass().getMethod("getKeyFrameInfo",
                                                                    Object.class, int.class, int[].class);
      }
      catch (NoSuchMethodException e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }

    if (myGetKeyFrameInfoMethod != null) {
      try {
        RenderService.getRenderAsyncActionExecutor().runAsyncAction(() -> {
          try {
            int[] tmpKeyInfo = Arrays.copyOf(keyInfo, keyInfo.length);
            int noOfKeyPosition = (Integer)myGetKeyFrameInfoMethod.invoke(myDesignTool, info.getViewObject(), type, tmpKeyInfo);
            myCachedKeyframeInfo.put(component.getId(), new KeyframeInfo(tmpKeyInfo, noOfKeyPosition));
          }
          catch (Exception e) {
            myGetKeyFrameInfoMethod = null;
            Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
          }
        });
      }
      catch (Exception e) {
        Logger.getInstance(MotionLayoutComponentHelper.class).debug(e);
      }
    }
  }

  public void setShowPaths(boolean show) {
    mShowPaths = show;
  }

  public boolean getShowPaths() {
    return mShowPaths;
  }

  public static void refresh(NlComponent component) {
    ViewInfo viewInfo = NlComponentHelperKt.getViewInfo(component);
    if (viewInfo != null) {
      ((View)viewInfo.getViewObject()).forceLayout();
    }
  }
}
