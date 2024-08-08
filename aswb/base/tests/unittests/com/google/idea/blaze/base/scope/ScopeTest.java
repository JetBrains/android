/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.scope;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.idea.blaze.base.BlazeTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Scope}. */
@RunWith(JUnit4.class)
public class ScopeTest extends BlazeTestCase {

  @Test
  public void testScopedOperationRuns() {
    final boolean[] ran = new boolean[1];
    Scope.root(
        new ScopedOperation() {
          @Override
          public void execute(@NotNull BlazeContext context) {
            ran[0] = true;
          }
        });
    assertTrue(ran[0]);
  }

  @Test
  public void testScopedFunctionReturnsValue() {
    String result =
        Scope.root(
            new ScopedFunction<String>() {
              @Override
              public String execute(@NotNull BlazeContext context) {
                return "test";
              }
            });
    assertThat(result).isEqualTo("test");
  }

  @Test
  public void testScopedOperationEndsContext() {
    final BlazeScope scope = mock(BlazeScope.class);
    Scope.root(
        new ScopedOperation() {
          @Override
          public void execute(@NotNull BlazeContext context) {
            context.push(scope);
          }
        });
    verify(scope).onScopeEnd(any(BlazeContext.class));
  }

  @Test
  public void testScopedFunctionEndsContext() {
    final BlazeScope scope = mock(BlazeScope.class);
    Scope.root(
        new ScopedFunction<String>() {
          @Override
          public String execute(@NotNull BlazeContext context) {
            context.push(scope);
            return "";
          }
        });
    verify(scope).onScopeEnd(any(BlazeContext.class));
  }

  @Test
  public void testCancelledParentContextCancelsChildContext() {
    BlazeContext parentContext = BlazeContext.create();
    Scope.push(
        parentContext,
        childContext -> {
          parentContext.setCancelled();
          assertThat(childContext.isCancelled()).isTrue();
        });
  }

  /*
  @Test
  public void testThrowingExceptionEndsScopedOperationWithFailure() {
    final RuntimeException e = new RuntimeException();
    final BlazeScope scope = mock(BlazeScope.class);
    Throwable throwable = Scope.root(project, new ScopedOperation() {
      @Override
      public void execute(@NotNull BlazeContext context) {
        context.push(scope);
        throw e;
      }
    }).throwable;
    verify(scope).onScopeEnd(any(BlazeContext.class));
    assertThat(e).isEqualTo(throwable);
  }

  @Test
  public void testThrowingExceptionEndsScopeFunctionWithFailure() {
    final RuntimeException e = new RuntimeException();
    final BlazeScope scope = mock(BlazeScope.class);
    Throwable throwable = Scope.root(project, new ScopedFunction<String>() {
      @Override
      public String execute(@NotNull BlazeContext context) {
        context.push(scope);
        throw e;
      }
    }).throwable;
    verify(scope).onScopeEnd(any(BlazeContext.class));
    assertThat(e).isEqualTo(throwable);
  }
  */
}
