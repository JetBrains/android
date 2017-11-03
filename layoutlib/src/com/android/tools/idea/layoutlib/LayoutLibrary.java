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

package com.android.tools.idea.layoutlib;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.DrawableParams;
import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.Result.Status;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.sdk.LoadStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static com.android.ide.common.rendering.api.Result.Status.ERROR_REFLECTION;

/**
 * Class to use the Layout library.
 * <p>
 * Use {@link #load(Bridge, ClassLoader)} to get an instance
 * <p>
 * Use the layout library with:
 * {@link #init}, {@link #supports(int)}, {@link #createSession(SessionParams)},
 * {@link #dispose()}, {@link #clearCaches(Object)}.
 */
public class LayoutLibrary {

    /** Link to the layout bridge */
    private final Bridge mBridge;
    /** classloader used to load the jar file */
    private final ClassLoader mClassLoader;

    // Reflection data for older Layout Libraries.
    private Method mViewGetParentMethod;
    private Method mViewGetBaselineMethod;
    private Class<?> mMarginLayoutParamClass;
    private Field mLeftMarginField;
    private Field mTopMarginField;
    private Field mRightMarginField;
    private Field mBottomMarginField;

    /**
     * Returns the classloader used to load the classes in the layoutlib jar file.
     */
    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    /**
     * Returns a {@link LayoutLibrary} instance using the given {@link Bridge} and {@link ClassLoader}
     */
    static LayoutLibrary load(Bridge bridge, ClassLoader classLoader) {
        return new LayoutLibrary(bridge, classLoader);
    }

    private LayoutLibrary(Bridge bridge,  ClassLoader classLoader) {
        mBridge = bridge;
        mClassLoader = classLoader;
    }


    // ------ Layout Lib API proxy

    /**
     * Returns the API level of the layout library.
     */
    public int getApiLevel() {
        if (mBridge != null) {
            return mBridge.getApiLevel();
        }

        return 0;
    }

    /**
     * Returns the revision of the library inside a given (layoutlib) API level.
     * The true version number of the library is {@link #getApiLevel()}.{@link #getRevision()}
     */
    public int getRevision() {
        if (mBridge != null) {
            return mBridge.getRevision();
        }

        return 0;
    }

    /**
     * Returns whether the LayoutLibrary supports a given {@link Capability}.
     * @return true if it supports it.
     *
     * @see Bridge#getCapabilities()
     *
     * @deprecated use {@link #supports(int)}
     */
    @Deprecated
    public boolean supports(Capability capability) {
        return supports(capability.ordinal());
    }

    /**
     * Returns whether the LayoutLibrary supports a given {@link Features}.
     *
     * @see Bridge#supports(int)
     */
    public boolean supports(int capability) {
        if (mBridge != null) {
            if (mBridge.getApiLevel() > 12) {
                // Features were introduced in API level 13.
                return mBridge.supports(capability);
            } else {
                return capability <= Features.LAST_CAPABILITY
                        && mBridge.getCapabilities().contains(Capability.values()[capability]);
            }
        }

        return false;
    }

    /**
     * Initializes the Layout Library object. This must be called before any other action is taken
     * on the instance.
     *
     * @param platformProperties The build properties for the platform.
     * @param fontLocation the location of the fonts in the SDK target.
     * @param enumValueMap map attrName ⇒ { map enumFlagName ⇒ Integer value }. This is typically
     *          read from attrs.xml in the SDK target.
     * @param log a {@link LayoutLog} object. Can be null.
     * @return true if success.
     */
    public boolean init(Map<String, String> platformProperties,
            File fontLocation,
            Map<String, Map<String, Integer>> enumValueMap,
            LayoutLog log) {
        if (mBridge != null) {
            return mBridge.init(platformProperties, fontLocation, enumValueMap, log);
        }

        return false;
    }

    /**
     * Prepares the layoutlib to unloaded.
     *
     * @see Bridge#dispose()
     */
    public boolean dispose() {
        if (mBridge != null) {
            return mBridge.dispose();
        }

        return true;
    }

    /**
     * Starts a layout session by inflating and rendering it. The method returns a
     * {@link RenderSession} on which further actions can be taken.
     * <p>
     * Before taking further actions on the scene, it is recommended to use
     * {@link #supports(int)} to check what the scene can do.
     *
     * @return a new {@link RenderSession} object that contains the result of the scene creation and
     * first rendering or null if {@link #getStatus()} doesn't return {@link LoadStatus#LOADED}.
     *
     * @see Bridge#createSession(SessionParams)
     */
    public RenderSession createSession(SessionParams params) {
        if (mBridge != null) {
            RenderSession session = mBridge.createSession(params);
            if (params.getExtendedViewInfoMode() && !supports(Features.EXTENDED_VIEWINFO)) {
                // Extended view info was requested but the layoutlib does not support it.
                // Add it manually.
                List<ViewInfo> infoList = session.getRootViews();
                if (infoList != null) {
                    for (ViewInfo info : infoList) {
                        addExtendedViewInfo(info);
                    }
                }
            }

            return session;
        }

        return null;
    }

    /**
     * Renders a Drawable. If the rendering is successful, the result image is accessible through
     * {@link Result#getData()}. It is of type {@link BufferedImage}
     * @param params the rendering parameters.
     * @return the result of the action.
     */
    public Result renderDrawable(DrawableParams params) {
        if (mBridge != null) {
            return mBridge.renderDrawable(params);
        }

        return Status.NOT_IMPLEMENTED.createResult();
    }

    /**
     * Clears the resource cache for a specific project.
     * <p>This cache contains bitmaps and nine patches that are loaded from the disk and reused
     * until this method is called.
     * <p>The cache is not configuration dependent and should only be cleared when a
     * resource changes (at this time only bitmaps and 9 patches go into the cache).
     *
     * @param projectKey the key for the project.
     *
     * @see Bridge#clearCaches(Object)
     */
    public void clearCaches(Object projectKey) {
        if (mBridge != null) {
            mBridge.clearCaches(projectKey);
        }
    }

    /**
     * Utility method returning the parent of a given view object.
     *
     * @param viewObject the object for which to return the parent.
     *
     * @return a {@link Result} indicating the status of the action, and if success, the parent
     *      object in {@link Result#getData()}
     */
    public Result getViewParent(Object viewObject) {
        if (mBridge != null) {
            Result r = mBridge.getViewParent(viewObject);
            if (r.isSuccess()) {
                return r;
            }
        }

        return getViewParentWithReflection(viewObject);
    }

    /**
     * Returns true if the character orientation of the locale is right to left.
     * @param locale The locale formatted as language-region
     * @return true if the locale is right to left.
     */
    public boolean isRtl(String locale) {
        return supports(Features.RTL) && mBridge != null && mBridge.isRtl(locale);
    }

    // ------ Implementation

    private Result getViewParentWithReflection(Object viewObject) {
        // default implementation using reflection.
        try {
            if (mViewGetParentMethod == null) {
                Class<?> viewClass = Class.forName("android.view.View");
                mViewGetParentMethod = viewClass.getMethod("getParent");
            }

            return Status.SUCCESS.createResult(mViewGetParentMethod.invoke(viewObject));
        } catch (Exception e) {
            // Catch all for the reflection calls.
            return ERROR_REFLECTION.createResult(null, e);
        }
    }

    private void addExtendedViewInfo(@NotNull ViewInfo info) {
        computeExtendedViewInfo(info);

        List<ViewInfo> children = info.getChildren();
        for (ViewInfo child : children) {
            addExtendedViewInfo(child);
        }
    }

    private void computeExtendedViewInfo(@NotNull ViewInfo info) {
        Object viewObject = info.getViewObject();
        Object params = info.getLayoutParamsObject();

        int baseLine = getViewBaselineReflection(viewObject);
        int leftMargin = 0;
        int topMargin = 0;
        int rightMargin = 0;
        int bottomMargin = 0;

        try {
            if (mMarginLayoutParamClass == null) {
                mMarginLayoutParamClass = Class.forName(
                        "android.view.ViewGroup$MarginLayoutParams");

                mLeftMarginField = mMarginLayoutParamClass.getField("leftMargin");
                mTopMarginField = mMarginLayoutParamClass.getField("topMargin");
                mRightMarginField = mMarginLayoutParamClass.getField("rightMargin");
                mBottomMarginField = mMarginLayoutParamClass.getField("bottomMargin");
            }

            if (mMarginLayoutParamClass.isAssignableFrom(params.getClass())) {

                leftMargin = (Integer)mLeftMarginField.get(params);
                topMargin = (Integer)mTopMarginField.get(params);
                rightMargin = (Integer)mRightMarginField.get(params);
                bottomMargin = (Integer)mBottomMarginField.get(params);
            }

        } catch (Exception e) {
            // just use 'unknown' value.
            leftMargin = Integer.MIN_VALUE;
            topMargin = Integer.MIN_VALUE;
            rightMargin = Integer.MIN_VALUE;
            bottomMargin = Integer.MIN_VALUE;
        }

        info.setExtendedInfo(baseLine, leftMargin, topMargin, rightMargin, bottomMargin);
    }

    /**
     * Utility method returning the baseline value for a given view object. This basically returns
     * View.getBaseline().
     *
     * @param viewObject the object for which to return the index.
     *
     * @return the baseline value or -1 if not applicable to the view object or if this layout
     *     library does not implement this method.
     */
    private int getViewBaselineReflection(Object viewObject) {
        // default implementation using reflection.
        try {
            if (mViewGetBaselineMethod == null) {
                Class<?> viewClass = Class.forName("android.view.View");
                mViewGetBaselineMethod = viewClass.getMethod("getBaseline");
            }

            Object result = mViewGetBaselineMethod.invoke(viewObject);
            if (result instanceof Integer) {
                return ((Integer)result).intValue();
            }

        } catch (Exception e) {
            // Catch all for the reflection calls.
        }

        return Integer.MIN_VALUE;
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected LayoutLibrary() {
        mBridge = null;
        mClassLoader = null;
    }
}
