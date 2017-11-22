/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.profilers.cpu;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Holds characteristics of Java methods, C++ functions and syscalls.
 */
public class MethodModel {
  @NotNull private final String myName;

  /**
   * Method's full class name (e.g. java.lang.String or art::interpreter::SomeClass).
   * For native methods, there is also the scenario where the function doesn't belong to a particular class (e.g. art::bla::Method).
   * In these scenarios, this field stores the full namespace (e.g. art::bla).
   */
  @NotNull private final String myClassOrNamespace;

  /**
   * Combination of characters used to separate packages, namespaces, class and method names.
   * E.g. "::" is the separator of "art::SomeClass::Method()" and "." is the separator of "java.lang.String.toString()"
   */
  @NotNull private final String mySeparator;

  /**
   * Signature of a method, encoded by java type encoding.
   *
   * For example:
   * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)}
   * the signature is (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
   *
   * Java encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
   */
  @NotNull private final String mySignature;

  /**
   * List of the method's parameters, comma separated (e.g. "int, float").
   */
  @NotNull private final String myParameters;

  private String myFullName;

  private String myId;

  /**
   * Whether the method is native.
   */
  private boolean myNative;

  private MethodModel(Builder builder) {
    myName = builder.myName;
    myClassOrNamespace = builder.myNative ? builder.myNamespaceAndClass : builder.myJavaClassName;
    mySeparator = builder.mySeparator;
    mySignature = builder.mySignature;
    myParameters = builder.myParameters;
    myNative = builder.myNative;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getClassOrNamespace() {
    return myClassOrNamespace;
  }

  @NotNull
  public String getSignature() {
    return mySignature;
  }

  @NotNull
  public String getParameters() {
    return myParameters;
  }

  @NotNull
  public String getSeparator() {
    return mySeparator;
  }

  public boolean isNative() {
    return myNative;
  }

  @NotNull
  public String getFullName() {
    // Separator is only needed if we have a class name, otherwise we're gonna end up with a leading separator.
    // We don't have a class name, for instance, for native methods (e.g. clock_gettime)
    // or the special nodes created to represent a thread (e.g. AsyncTask #1).
    if (myFullName == null) {
      String separator = StringUtil.isEmpty(myClassOrNamespace) ? "" : mySeparator;
      myFullName = String.format("%s%s%s", myClassOrNamespace, separator, myName);
    }
    return myFullName;
  }


  public String getId() {
    if (myId == null) {
      myId = String.format("%s%s", getFullName(), myNative ? myParameters : mySignature);
    }
    return myId;
  }

  public static class Builder {
    @NotNull private final String myName;
    /**
     * Full java class name, including packages. E.g. "java.util.List.add".
     */
    @NotNull private String myJavaClassName;
    /**
     * Method's namespace. Also includes the class name if the method belongs to a class (i.e. if it is a function). For example:
     * "art::interpreter::SomeClass" (including class) or "art" (namespace only).
     */
    @NotNull private String myNamespaceAndClass;
    @NotNull private String mySeparator;
    @NotNull private String mySignature;
    @NotNull private String myParameters;
    private boolean myNative;

    public Builder(@NotNull String name) {
      myName = name;
      myJavaClassName = "";
      myNamespaceAndClass = "";
      mySeparator = "";
      mySignature = "";
      myParameters = "";
    }

    public Builder setJavaClassName(@NotNull String javaClassName) {
      if (!myNamespaceAndClass.isEmpty()) {
        throw new IllegalStateException("Java class name can't be set if a native namespace/class was already set");
      }
      myJavaClassName = javaClassName;
      mySeparator = ".";
      return this;
    }

    public Builder setNativeNamespaceAndClass(@NotNull String nativeNamespaceAndClass) {
      if (!myJavaClassName.isEmpty()) {
        throw new IllegalStateException("Native namespace/class can't be set if a Java class name was already set");
      }
      myNamespaceAndClass = nativeNamespaceAndClass;
      mySeparator = "::";
      myNative = true;
      return this;
    }

    public Builder setSignature(@NotNull String signature) {
      mySignature = signature;
      return this;
    }

    public Builder setParameters(@NotNull String parameters) {
      myParameters = parameters;
      return this;
    }

    public MethodModel build() {
      return new MethodModel(this);
    }
  }
}
