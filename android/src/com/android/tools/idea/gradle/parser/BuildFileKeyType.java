/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.parser.BuildFileKey.escapeLiteralString;
import static com.android.tools.idea.gradle.parser.GradleBuildFile.UNRECOGNIZED_VALUE;

/**
 * Enumerates the type information for build file parameters located by {@linkplain BuildFileKey}. The individual types have code to
 * actually set, get, and determine validity of values.
 */
public enum BuildFileKeyType {
  STRING(String.class, "''") {
    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject()).createLiteralFromValue(value.toString()));
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      return "'" + escapeLiteralString(value.toString()) + "'";
    }
  },
  INTEGER(Integer.class, "0"),

  /**
   * INTEGER_OR_STRING is for properties that have overloaded Groovy setters that can take either type. This is used by
   * {@link com.android.tools.idea.gradle.parser.BuildFileKey#COMPILE_SDK_VERSION} et al, where you can specify an integer API level
   * or a string platform codename. If you retrieve properties with this type, you will always get a {@link String} value regardless
   * of the type used to represent it in the build file; when you set the property, it will examine the value and use either Groovy
   * integers or strings as appropriate to represent the value.
   */
  INTEGER_OR_STRING(String.class, "0") {
    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      String valueString = value.toString();
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(arg.getProject());
      if (isInteger(valueString)) {
        arg.replace(factory.createExpressionFromText(valueString));
      } else {
        arg.replace(factory.createLiteralFromValue(value));
      }
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      String valueString = value.toString();
      if (isInteger(valueString)) {
        return valueString;
      } else {
        return "'" + escapeLiteralString(value.toString()) + "'";
      }
    }

    @Override
    @Nullable
    public Object getValue(@NotNull GroovyPsiElement arg) {
      if (!(arg instanceof GrLiteral)) {
        return UNRECOGNIZED_VALUE;
      }
      Object value = ((GrLiteral) arg).getValue();
      return value != null ? value.toString() : null;
    }
  },
  BOOLEAN(Boolean.class, "false"),
  CLOSURE(List.class, "{}") {
    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      // Just create an empty closure. Don't try to populate it; that will be done elsewhere.
      return "{\n}";
    }
  },
  FILE(File.class, Constants.FILE_METHOD_CALL + "('')") { // Represented in Groovy as file('/path/to/file')

    @Nullable
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      if (!(arg instanceof GrMethodCall)) {
        return UNRECOGNIZED_VALUE;
      }
      GrMethodCall call = (GrMethodCall)arg;
      if (!(Constants.FILE_METHOD_CALL.equals(GradleGroovyFile.getMethodCallName(call)))) {
        return UNRECOGNIZED_VALUE;
      }
      Object path = GradleGroovyFile.getFirstLiteralArgumentValue(call);
      if (path == null) {
        return UNRECOGNIZED_VALUE;
      }
      return new File(path.toString());
    }

    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject())
                    .createStatementFromText(Constants.FILE_METHOD_CALL + "('" + getFilePath(value, true) + "')"));
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      return Constants.FILE_METHOD_CALL + "('" + getFilePath(value, true) + "')";
    }
  },
  FILE_AS_STRING(File.class, "''") { // Represented in Groovy as '/path/to/file'

    @Nullable
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      if (!(arg instanceof GrLiteral)) {
        return UNRECOGNIZED_VALUE;
      }
      Object value = ((GrLiteral) arg).getValue();
      return value != null ? new File(value.toString()) : null;
    }

    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject()).createLiteralFromValue(getFilePath(value, false)));
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      return "'" + getFilePath(value, true) + "'";
    }
  },
  REFERENCE(String.class, "reference") {
    @Nullable
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      return arg.getText();
    }
  };

  private final Class<?> myNativeType;
  private final String myDefaultValue;

  BuildFileKeyType(@NotNull Class<?> nativeType, @NotNull String defaultValue) {
    myNativeType = nativeType;
    myDefaultValue = defaultValue;
  }

  @NotNull
  public Class<?> getNativeType() {
    return myNativeType;
  }

  @NotNull
  public String convertValueToExpression(@NotNull Object value) {
    return value.toString();
  }

  private static String getFilePath(Object value, boolean escape) {
    String path = FileUtil.toSystemIndependentName(((File)value).getPath());
    return escape ? escapeLiteralString(path) : path;
  }

  @Nullable
  public Object getValue(@NotNull GroovyPsiElement arg) {
    if (!(arg instanceof GrLiteral)) {
      return UNRECOGNIZED_VALUE;
    }
    return ((GrLiteral) arg).getValue();
  }

  public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
    arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject()).createExpressionFromText(value.toString()));
  }

  private static boolean isInteger(@NotNull String s) {
    try {
      Integer.parseInt(s);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private static class Constants {
    private static final String FILE_METHOD_CALL = "file";
  }
}
