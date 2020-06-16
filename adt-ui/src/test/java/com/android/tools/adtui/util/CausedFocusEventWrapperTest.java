// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.adtui.util;

import com.intellij.openapi.util.SystemInfoRt;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class CausedFocusEventWrapperTest {

  // Main purpose of these tests is to check that there are no misprints in enum value names. These tests instantiate enum values, pass
  // instances to CausedFocusEventWrapper and checks that CausedFocusEventWrapper recognizes them properly.

  @Test
  public void testIsTraversal() {
    CausedFocusEventWrapper inst = new CausedFocusEventWrapper(getEnumValue("TRAVERSAL"));
    Map<String, Boolean> res = invokeAll(inst);
    assertSingleTrueValueAtKey("isTraversal", res);
  }

  @Test
  public void testIsTraversalForward() {
    CausedFocusEventWrapper inst = new CausedFocusEventWrapper(getEnumValue("TRAVERSAL_FORWARD"));
    Map<String, Boolean> res = invokeAll(inst);
    assertSingleTrueValueAtKey("isTraversalForward", res);
  }

  @Test
  public void testIsTraversalBackward() {
    CausedFocusEventWrapper inst = new CausedFocusEventWrapper(getEnumValue("TRAVERSAL_BACKWARD"));
    Map<String, Boolean> res = invokeAll(inst);
    assertSingleTrueValueAtKey("isTraversalBackward", res);
  }

  @Test
  public void testIsTraversalUp() {
    CausedFocusEventWrapper inst = new CausedFocusEventWrapper(getEnumValue("TRAVERSAL_UP"));
    Map<String, Boolean> res = invokeAll(inst);
    assertSingleTrueValueAtKey("isTraversalUp", res);
  }

  @Test
  public void testIsTraversalDown() {
    CausedFocusEventWrapper inst = new CausedFocusEventWrapper(getEnumValue("TRAVERSAL_DOWN"));
    Map<String, Boolean> res = invokeAll(inst);
    assertSingleTrueValueAtKey("isTraversalDown", res);
  }

  @Test
  public void testIsFocusEventWithCause() {
    if (SystemInfoRt.IS_AT_LEAST_JAVA9) {
      Assert.assertTrue(CausedFocusEventWrapper.isFocusEventWithCause(new FocusEvent(Mockito.mock(Component.class), 0)));
    }
  }

  private void assertSingleTrueValueAtKey(String key, Map<String, Boolean> res) {
    Assert.assertTrue(res.entrySet().stream().allMatch(e -> e.getValue() == e.getKey().equals(key)));
  }

  private static Map<String, Boolean> invokeAll(CausedFocusEventWrapper inst) {
    Map<String, Boolean> res = new HashMap<>();
    res.put("isTraversal", inst.isTraversal());
    res.put("isTraversalBackward", inst.isTraversalBackward());
    res.put("isTraversalDown", inst.isTraversalDown());
    res.put("isTraversalForward", inst.isTraversalForward());
    res.put("isTraversalUp", inst.isTraversalUp());
    return res;
  }

  private static Enum<?> getEnumValue(String name) {
    try {
      Class<Enum> causeClass = (Class<Enum>)Class.forName("java.awt.event.FocusEvent$Cause");
      return Enum.valueOf(causeClass, name);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}