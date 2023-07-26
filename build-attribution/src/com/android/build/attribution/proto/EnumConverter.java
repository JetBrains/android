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
package com.android.build.attribution.proto;

import com.android.utils.HelpfulEnumConverter;

public class EnumConverter<A extends Enum<?>, B extends Enum<?>> {
  private Class<A> aClass;
  private Class<B> bClass;

  public EnumConverter(Class<A> aClass, Class<B> bClass) {
    this.aClass = aClass;
    this.bClass = bClass;
  }

  public B aToB(A a) {
    return silent(a, bClass);
  }

  public A bToA(B b) {
    return silent(b, aClass);
  }

  private <T extends Enum<?>, K extends Enum<?>> K silent(T value, Class<K> otherClass) {
    Object convert = new HelpfulEnumConverter(otherClass).convert(value.name());

    if (convert == null) throw new IllegalStateException(
      "Class " + otherClass + " does not contain enum with name " + value.name() + ". Conversion is impossible");

    return (K)convert;
  }
}