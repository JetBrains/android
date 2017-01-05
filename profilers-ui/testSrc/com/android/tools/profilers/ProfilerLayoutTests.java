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
package com.android.tools.profilers;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;

public class ProfilerLayoutTests {

  @Test
  public void testCannotInstanciate() throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Constructor<?>[] constructors = ProfilerLayout.class.getDeclaredConstructors();
    assertEquals(1, constructors.length);
    assertEquals(false, constructors[0].isAccessible());
    constructors[0].setAccessible(true);
    constructors[0].newInstance();
  }
}
