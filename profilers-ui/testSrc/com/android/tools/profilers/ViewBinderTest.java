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
package com.android.tools.profilers;

import org.junit.Test;

import static org.junit.Assert.*;

public class ViewBinderTest {
  @Test
  public void test() {
    ViewBinder<TestParentView, TestModel, TestView> viewBinder = new ViewBinder<>();
    viewBinder.bind(TestModel.class, TestView::new);
    assertNotNull(viewBinder.build(new TestParentView(), new TestModel()));
  }

  @Test
  public void testInheritance() {
    ViewBinder<TestParentView, TestModel, TestView> viewBinder = new ViewBinder<>();
    viewBinder.bind(TestModelExtended.class, TestViewExtended::new);
    TestView view = viewBinder.build(new TestParentViewExtended(), new TestModelExtended());
    assertTrue(view instanceof TestViewExtended);
  }

  private static class TestParentView {}
  private static class TestModel {}
  private static class TestView {
    TestView(TestParentView parentView, TestModel model) {}
  }

  private static class TestParentViewExtended extends TestParentView {}
  private static class TestModelExtended extends TestModel {}
  private static class TestViewExtended extends TestView {
    TestViewExtended(TestParentViewExtended parentView, TestModelExtended model) {
      super(parentView, model);
    }
  }
}