/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.screensharing;

import static android.os.Build.VERSION.SDK_INT;
import static com.android.tools.screensharing.Main.ATTRIBUTION_TAG;

import android.content.ClipData;
import android.os.PersistableBundle;
import android.util.Log;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Facilitates operations with Android clipboard by calling hidden methods of
 * android.content.IClipboard through reflection.
 */
@SuppressWarnings("unused") // Called through JNI.
public class ClipboardAdapter {
  private static final String PACKAGE_NAME = "com.android.shell";
  private static final int USER_ID = 0;
  private static final int DEVICE_ID_DEFAULT = 0; // From android.companion.virtual.VirtualDeviceManager

  private static Object clipboard;
  private static Method getPrimaryClipMethod;
  private static Method setPrimaryClipMethod;
  private static Method addPrimaryClipChangedListenerMethod;
  private static Method removePrimaryClipChangedListenerMethod;
  private static ClipboardListener clipboardListener;
  private static PersistableBundle overlaySuppressor;

  static {
    clipboard = ServiceManager.getServiceAsInterface("clipboard", "android/content/IClipboard", true);

    if (clipboard == null) {
      Log.w(ATTRIBUTION_TAG, "Could not find \"clipboard\" service - clipboard synchronization is not possible.");
    }
    else {
      try {
        Class<?> clipboardClass = clipboard.getClass();
        Method[] methods = clipboardClass.getDeclaredMethods();
        getPrimaryClipMethod = findMethodAndMakeAccessible(methods, "getPrimaryClip");
        setPrimaryClipMethod = findMethodAndMakeAccessible(methods, "setPrimaryClip");
        addPrimaryClipChangedListenerMethod = findMethodAndMakeAccessible(methods, "addPrimaryClipChangedListener");
        removePrimaryClipChangedListenerMethod = findMethodAndMakeAccessible(methods, "removePrimaryClipChangedListener");
        if (checkNumberOfParameters(getPrimaryClipMethod, 0, 5) &&
            checkNumberOfParameters(setPrimaryClipMethod, 1, 6) &&
            checkNumberOfParameters(addPrimaryClipChangedListenerMethod, 1, 5) &&
            checkNumberOfParameters(removePrimaryClipChangedListenerMethod, 1, 5)) {
          clipboardListener = new ClipboardListener();
          if (SDK_INT >= 33) {
            overlaySuppressor = new PersistableBundle(1);
            overlaySuppressor.putBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY", true);
          }
        }
        else {
          clipboard = null;
        }
      }
      catch (NoSuchMethodException e) {
        Log.e(ATTRIBUTION_TAG, "Unable to find the IClipboard." + e.getMessage() + " method");
        clipboard = null;
      }
    }
  }

  public static String getText() throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return "";
    }

    int numberOfParameters = getPrimaryClipMethod.getParameterCount();
    ClipData clipData = numberOfParameters == 0 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard) :
                        numberOfParameters == 1 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME) :
                        numberOfParameters == 2 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, USER_ID) :
                        numberOfParameters == 3 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID) :
                        numberOfParameters == 4 ?
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT) :
                        numberOfParameters == 5 ?
                        // This non-standard method signature is used on Honor Magic4 Pro API 34 (b/342961840).
                        (ClipData)getPrimaryClipMethod.invoke(clipboard, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT, null) :
                        null;
    if (clipData == null || clipData.getItemCount() == 0) {
      return "";
    }
    return clipData.getItemAt(0).getText().toString();
  }

  public static void setText(String text) throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return;
    }

    ClipData clipData = ClipData.newPlainText(text, text);
    if (SDK_INT >= 33) {
      // Suppress clipboard change UI overlay on Android 13+.
      clipData.getDescription().setExtras(overlaySuppressor);
    }

    int numberOfParameters = setPrimaryClipMethod.getParameterCount();
    if (numberOfParameters == 1) {
      setPrimaryClipMethod.invoke(clipboard, clipData);
    }
    else if (numberOfParameters == 2) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME);
    }
    else if (numberOfParameters == 3) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, USER_ID);
    }
    else if (numberOfParameters == 4) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID);
    }
    else if (numberOfParameters == 5) {
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT);
    }
    else if (numberOfParameters == 6) {
      // This non-standard method signature is used on Honor Magic4 Pro API 34 (b/342961840).
      setPrimaryClipMethod.invoke(clipboard, clipData, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT, true);
    }
  }

  public static void enablePrimaryClipChangedListener() throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return;
    }

    int numberOfParameters = addPrimaryClipChangedListenerMethod.getParameterCount();
    if (numberOfParameters == 1) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME);
    }
    else if (numberOfParameters == 2) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME);
    }
    else if (numberOfParameters == 3) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, USER_ID);
    }
    else if (numberOfParameters == 4) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID);
    }
    else if (numberOfParameters == 5) {
      addPrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID, DEVICE_ID_DEFAULT);
    }
  }

  public static void disablePrimaryClipChangedListener() throws InvocationTargetException, IllegalAccessException {
    if (clipboard == null) {
      return;
    }

    int numberOfParameters = removePrimaryClipChangedListenerMethod.getParameterCount();
    if (numberOfParameters == 1) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener);
    }
    else if (numberOfParameters == 2) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME);
    }
    else if (numberOfParameters == 3) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, USER_ID);
    }
    else if (numberOfParameters == 4) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID);
    }
    else if (numberOfParameters == 5) {
      removePrimaryClipChangedListenerMethod.invoke(clipboard, clipboardListener, PACKAGE_NAME, ATTRIBUTION_TAG, USER_ID,
                                                    DEVICE_ID_DEFAULT);
    }
  }

  private static Method findMethodAndMakeAccessible(Method[] methods, String name) throws NoSuchMethodException {
    for (Method method : methods) {
      if (method.getName().equals(name)) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new NoSuchMethodException(name);
  }

  private static boolean checkNumberOfParameters(Method method, int minParam, int maxParam) {
    int parameterCount = method.getParameterCount();
    if (minParam <= parameterCount && parameterCount <= maxParam) {
      return true;
    }

    Log.e(ATTRIBUTION_TAG, "Unexpected number of IClipboard." + method.getName() + " parameters: " + parameterCount +
                           " types: " + getParameterTypesString(method));
    return false;
  }

  private static String getParameterTypesString(Method method) {
    Class<?>[] parameterTypes = method.getParameterTypes();
    StringBuilder types = new StringBuilder();
    for (Class<?> parameterType : parameterTypes) {
      if (types.length() > 0) {
        types.append(", ");
      }
      types.append(parameterType.getName());
    }
    return types.toString();
  }
}
