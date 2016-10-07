/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.debug;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.google.common.util.concurrent.Atomics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class AnnotationsRenderer {
  public static class Result {
    @NotNull public final String label;
    @Nullable public final Icon icon;

    public Result(@NotNull String label, @Nullable Icon icon) {
      this.label = label;
      this.icon = icon;
    }
  }

  @NotNull
  public static Result render(@Nullable ResourceIdResolver resolver,
                              @NotNull PsiAnnotation annotation,
                              int value) {
    String qualifiedName = getQualifiedName(annotation);
    if (qualifiedName == null) {
      return renderUnknown(null, value);
    }

    if (ResourceEvaluator.COLOR_INT_ANNOTATION.equals(qualifiedName)) {
      return renderColorInt(value);
    }
    else if (qualifiedName.endsWith(ResourceEvaluator.RES_SUFFIX)) {
      return renderResourceRefAnnotation(resolver, value, qualifiedName);
    }
    else if (qualifiedName.equals(SdkConstants.INT_DEF_ANNOTATION)) {
      return renderIntDefAnnotation(annotation, value);
    }

    return renderUnknown(qualifiedName, value);
  }

  @NotNull
  private static Result renderIntDefAnnotation(@NotNull final PsiAnnotation annotation, final int value) {
    final AtomicReference<AndroidResolveHelper.IntDefResolution> valuesRef = Atomics.newReference();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        valuesRef.set(AndroidResolveHelper.resolveIntDef(annotation));
      }
    });

    AndroidResolveHelper.IntDefResolution intDef = valuesRef.get();
    if (intDef.valuesMap == null) {
      renderUnknown("IntDef", value);
    }

    return new Result(String.format(Locale.US, "0x%1$08x {%2$s}", value, renderIntDef(value, intDef)), null);
  }

  @NotNull
  static String renderIntDef(int value, AndroidResolveHelper.IntDefResolution intDef) {
    if (intDef.valuesMap == null) {
      return "";
    }

    if (!intDef.canBeOred) {
      String name = intDef.valuesMap.get(value);
      return StringUtil.notNullize(name);
    }

    StringBuilder sb = new StringBuilder(20);
    for (Map.Entry<Integer,String> entry : intDef.valuesMap.entrySet()) {
      int key = entry.getKey();
      if ((value & key) != 0) {
        if (sb.length() > 0) {
          sb.append(" | ");
        }
        sb.append(entry.getValue());
      }
    }

    return sb.toString();
  }

  @NotNull
  private static Result renderResourceRefAnnotation(@Nullable ResourceIdResolver resolver, int value, String qualifiedName) {
    String androidRes = null;
    if (resolver != null) {
      androidRes = resolver.getAndroidResourceName(value);
    }

    if (androidRes == null) {
      return renderUnknown(qualifiedName, value);
    } else {
      String result = String.format(Locale.US, "0x%1$08x {%2$s}", value, androidRes);
      return new Result(result, null);
    }
  }

  @NotNull
  private static Result renderColorInt(int value) {
    int alpha = value >>> 24;
    boolean hasAlpha = alpha != 0;

    //noinspection UseJBColor
    final Color color = new Color(value, hasAlpha);
    String result = String.format(Locale.US, "0x%1$08x {a=%2$02d r=%3$02d g=%4$02d b=%5$02d}", value, color.getAlpha(), color.getRed(),
                                  color.getGreen(), color.getBlue());
    return new Result(result, new ColorIcon(16, 12, color, true));
  }

  @NotNull
  private static Result renderUnknown(@Nullable String annotationName, int value) {
    return new Result(String.format("0x%1$08x {@%2$s ?}", value, annotationName == null ? "" : getSimpleClassName(annotationName)), null);
  }

  @Nullable
  private static String getQualifiedName(final PsiAnnotation annotation) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return annotation.getQualifiedName();
      }
    });
  }

  private static String getSimpleClassName(@NotNull String fqcn) {
    int index = fqcn.lastIndexOf('.');
    return (index < fqcn.length() - 1) ? fqcn.substring(index + 1) : fqcn;
  }
}
