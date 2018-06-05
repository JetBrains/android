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
package com.android.tools.swingp;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MethodStatTest {
  @Before
  public void before() {
    RenderStatsManager.setIsEnabled(true);
  }

  @After
  public void after() {
    RenderStatsManager.setIsEnabled(false);
  }

  @Test(expected = RuntimeException.class)
  public void catchesDoubleEnd() {
    BadTestClass bad = new BadTestClass();
    bad.doubleBad();
  }

  @Test
  public void methodStatEndsCorrect() {
    TestClass good = new TestClass();
    good.foo();
  }

  /**
   * Trivial extension of {@link MethodStat} (since it's abstract) to test its implementation.
   */
  private static final class TestMethodStat extends MethodStat {
    public TestMethodStat(@NotNull Object owner) {
      super(owner);
    }
  }

  private static final class TestClass {
    public void foo() {
      MethodStat stat = new TestMethodStat(this);
      stat.endMethod();
    }
  }

  private static final class BadTestClass {
    private final TestClass myTestClassAObject = new TestClass();

    public void doubleBad() {
      MethodStat stat = new TestMethodStat(this);
      myTestClassAObject.foo();
      stat.endMethod();
      stat.endMethod();
    }
  }
}
