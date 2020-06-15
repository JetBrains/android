// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.npw.template;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class KotlinVersionProviderTest {

  @Test
  public void testKotlinVersionWithReleaseWord() {
    KotlinVersionProvider inst = new KotlinVersionProvider("1.3.72-release-485");
    assertEquals("1.3.72", inst.getKotlinVersionForGradle());
  }

  @Test
  public void testPlainKotlinVersion() {
    KotlinVersionProvider inst = new KotlinVersionProvider("1.3.72");
    assertEquals("1.3.72", inst.getKotlinVersionForGradle());
  }
}