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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.common.Output;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Tests for {@link BlazeContext}. */
@RunWith(JUnit4.class)
@SuppressWarnings("MustBeClosedChecker")
public class BlazeContextTest extends BlazeTestCase {

  @Test
  public void testScopeBeginsWhenPushedToContext() {
    BlazeContext context = BlazeContext.create();
    final BlazeScope scope = mock(BlazeScope.class);
    context.push(scope);
    verify(scope).onScopeBegin(context);
  }

  @Test
  public void testScopeEndsWhenContextEnds() {
    BlazeContext context = BlazeContext.create();
    final BlazeScope scope = mock(BlazeScope.class);
    context.push(scope);
    context.close();
    verify(scope).onScopeEnd(context);
  }

  @Test
  public void testEndingTwiceHasNoEffect() {
    BlazeContext context = BlazeContext.create();
    final BlazeScope scope = mock(BlazeScope.class);
    context.push(scope);
    context.close();
    context.close();
    verify(scope).onScopeEnd(context);
  }

  @Test
  public void testEndingScopeNormallyDoesntEndParent() {
    BlazeContext parentContext = BlazeContext.create();
    BlazeContext childContext = BlazeContext.create(parentContext);
    childContext.close();
    assertTrue(childContext.isEnding());
    assertFalse(parentContext.isEnding());
  }

  @Test
  public void testCancellingScopeCancelsParent() {
    BlazeContext parentContext = BlazeContext.create();
    BlazeContext childContext = BlazeContext.create(parentContext);
    childContext.setCancelled();
    assertTrue(childContext.isCancelled());
    assertTrue(parentContext.isCancelled());
  }

  @Test
  public void testCancellingScopeCancelsChild() {
    BlazeContext parentContext = BlazeContext.create();
    BlazeContext childContext = BlazeContext.create(parentContext);
    parentContext.setCancelled();
    assertTrue(childContext.isCancelled());
    assertTrue(parentContext.isCancelled());
  }

  /** A simple scope that records its start and end by ID. */
  static class RecordScope implements BlazeScope {
    private final int id;
    private final List<String> record;

    public RecordScope(int id, List<String> record) {
      this.id = id;
      this.record = record;
    }

    @Override
    public void onScopeBegin(BlazeContext context) {
      record.add("begin" + id);
    }

    @Override
    public void onScopeEnd(BlazeContext context) {
      record.add("end" + id);
    }
  }

  @Test
  public void testScopesBeginAndEndInStackOrder() {
    List<String> record = Lists.newArrayList();
    BlazeContext context = BlazeContext.create();
    context
        .push(new RecordScope(1, record))
        .push(new RecordScope(2, record))
        .push(new RecordScope(3, record));
    context.close();
    assertThat(record)
        .isEqualTo(ImmutableList.of("begin1", "begin2", "begin3", "end3", "end2", "end1"));
  }

  @Test
  public void testParentFoundInStackOrder() {
    BlazeContext context = BlazeContext.create();
    BlazeScope scope1 = mock(BlazeScope.class);
    BlazeScope scope2 = mock(BlazeScope.class);
    BlazeScope scope3 = mock(BlazeScope.class);
    context.push(scope1).push(scope2).push(scope3);
    assertThat(context.getParentScope(scope3)).isEqualTo(scope2);
    assertThat(context.getParentScope(scope2)).isEqualTo(scope1);
    assertThat(context.getParentScope(scope1)).isNull();
  }

  @Test
  public void testParentFoundInStackOrderAcrossContexts() {
    BlazeContext parentContext = BlazeContext.create();
    BlazeContext childContext = BlazeContext.create(parentContext);
    BlazeScope scope1 = mock(BlazeScope.class);
    BlazeScope scope2 = mock(BlazeScope.class);
    BlazeScope scope3 = mock(BlazeScope.class);
    parentContext.push(scope1).push(scope2);
    childContext.push(scope3);
    assertThat(childContext.getParentScope(scope3)).isEqualTo(scope2);
  }

  static class TestOutput1 implements Output {}

  static class TestOutput2 implements Output {}

  static class TestOutputSink<T extends Output> implements OutputSink<T> {
    public boolean gotOutput;

    @Override
    public Propagation onOutput(T output) {
      gotOutput = true;
      return Propagation.Continue;
    }
  }

  static class TestOutputSink1 extends TestOutputSink<TestOutput1> {}

  static class TestOutputSink2 extends TestOutputSink<TestOutput2> {}

  @Test
  public void testOutputGoesToRegisteredSink() {
    BlazeContext context = BlazeContext.create();
    TestOutputSink1 sink = new TestOutputSink1();
    context.addOutputSink(TestOutput1.class, sink);

    assertFalse(sink.gotOutput);
    context.output(new TestOutput1());
    assertTrue(sink.gotOutput);
  }

  @Test
  public void testOutputDoesntGoToWrongSink() {
    BlazeContext context = BlazeContext.create();
    TestOutputSink2 sink = new TestOutputSink2();
    context.addOutputSink(TestOutput2.class, sink);

    assertFalse(sink.gotOutput);
    context.output(new TestOutput1());
    assertFalse(sink.gotOutput);
  }

  @Test
  public void testOutputGoesToParentContexts() {
    BlazeContext parentContext = BlazeContext.create();
    BlazeContext childContext = BlazeContext.create(parentContext);
    TestOutputSink1 sink = new TestOutputSink1();
    parentContext.addOutputSink(TestOutput1.class, sink);

    assertFalse(sink.gotOutput);
    childContext.output(new TestOutput1());
    assertTrue(sink.gotOutput);
  }

  @Test
  public void testHoldingPreventsEndingContext() {
    BlazeContext context = BlazeContext.create();
    context.hold();
    context.close();
    assertFalse(context.isEnding());
    context.release();
    assertTrue(context.isEnding());
  }

  private static class StringScope implements BlazeScope {

    public final String str;

    public StringScope(String s) {
      this.str = s;
    }
  }

  private static class CollectorScope implements BlazeScope {

    public final List<String> output;

    public CollectorScope(List<String> output) {
      this.output = output;
    }

    @Override
    public void onScopeEnd(BlazeContext context) {
      List<StringScope> scopes = context.getScopes(StringScope.class, this);
      for (StringScope scope : scopes) {
        output.add(scope.str);
      }
    }
  }

  @Test
  public void testGetScopesOnlyReturnsScopesLowerOnTheStack() {
    List<String> output1 = Lists.newArrayList();
    List<String> output2 = Lists.newArrayList();
    List<String> output3 = Lists.newArrayList();

    BlazeContext context = BlazeContext.create();
    context.push(new StringScope("a"));
    context.push(new StringScope("b"));
    CollectorScope scope = new CollectorScope(output1);
    context.push(scope);
    context.push(new StringScope("c"));
    context.push(new CollectorScope(output2));
    context.push(new StringScope("d"));
    context.push(new StringScope("e"));
    context.push(new CollectorScope(output3));
    context.close();

    assertThat(output1).isEqualTo(ImmutableList.of("b", "a"));
    assertThat(output2).isEqualTo(ImmutableList.of("c", "b", "a"));
    assertThat(output3).isEqualTo(ImmutableList.of("e", "d", "c", "b", "a"));
  }

  @Test
  public void testGetScopesOnlyReturnsScopesLowerOnTheStackForMultipleContexts() {
    List<String> output1 = Lists.newArrayList();
    List<String> output2 = Lists.newArrayList();
    List<String> output3 = Lists.newArrayList();

    BlazeContext context1 = BlazeContext.create();
    context1.push(new StringScope("a"));
    context1.push(new StringScope("b"));
    CollectorScope scope = new CollectorScope(output1);
    context1.push(scope);

    BlazeContext context2 = BlazeContext.create(context1);
    context2.push(new StringScope("c"));
    context2.push(new CollectorScope(output2));
    context2.push(new StringScope("d"));
    context2.push(new StringScope("e"));

    BlazeContext context3 = BlazeContext.create(context2);
    context3.push(new CollectorScope(output3));
    context3.close();
    context2.close();
    context1.close();

    assertThat(output1).isEqualTo(ImmutableList.of("b", "a"));
    assertThat(output2).isEqualTo(ImmutableList.of("c", "b", "a"));
    assertThat(output3).isEqualTo(ImmutableList.of("e", "d", "c", "b", "a"));
  }

  @Test
  public void testGetScopesOnlyReturnsScopesIfStartingScopeInContext() {
    List<String> output1 = Lists.newArrayList();

    BlazeContext context1 = BlazeContext.create();
    context1.push(new StringScope("a"));
    context1.push(new StringScope("b"));
    CollectorScope scope = new CollectorScope(output1);
    context1.push(scope);

    BlazeContext context2 = BlazeContext.create(context1);
    context2.push(new StringScope("c"));

    List<StringScope> scopes = context2.getScopes(StringScope.class, scope);
    assertThat(scopes).isEqualTo(ImmutableList.of());
  }

  @Test
  public void testGetScopesIncludesStartingScope() {
    BlazeContext context1 = BlazeContext.create();
    StringScope a = new StringScope("a");
    context1.push(a);
    StringScope b = new StringScope("b");
    context1.push(b);

    List<StringScope> scopes = context1.getScopes(StringScope.class, b);
    assertThat(scopes).isEqualTo(ImmutableList.of(b, a));
  }

  @Test
  public void testGetScopesIndexIsNoninclusive() {
    BlazeContext context1 = BlazeContext.create();
    StringScope scopeA = new StringScope("a");
    context1.push(scopeA);
    StringScope scopeB = new StringScope("b");
    context1.push(scopeB);

    List<StringScope> scopes = Lists.newArrayList();
    context1.getScopes(scopes, StringScope.class, 1);
    assertThat(scopes).isEqualTo(ImmutableList.of(scopeA));
  }

  @Test
  public void testGetScopesWithoutStartScopeGetsAll() {
    BlazeContext context1 = BlazeContext.create();
    StringScope a = new StringScope("a");
    context1.push(a);
    StringScope b = new StringScope("b");
    context1.push(b);

    List<StringScope> scopes = context1.getScopes(StringScope.class);
    assertThat(scopes).isEqualTo(ImmutableList.of(b, a));
  }

  static class NonPropagatingOutputSink implements OutputSink<TestOutput1> {
    boolean gotOutput;

    @Override
    public Propagation onOutput(TestOutput1 output) {
      this.gotOutput = true;
      return Propagation.Stop;
    }
  }

  @Test
  public void testOutputIsTerminatedByFirstSink() {
    NonPropagatingOutputSink sink1 = new NonPropagatingOutputSink();
    NonPropagatingOutputSink sink2 = new NonPropagatingOutputSink();
    NonPropagatingOutputSink sink3 = new NonPropagatingOutputSink();

    BlazeContext context1 = BlazeContext.create();
    context1.addOutputSink(TestOutput1.class, sink1);

    BlazeContext context2 = BlazeContext.create(context1);
    context2.addOutputSink(TestOutput1.class, sink2);
    context2.addOutputSink(TestOutput1.class, sink3);

    context2.output(new TestOutput1());

    assertThat(sink1.gotOutput).isFalse();
    assertThat(sink2.gotOutput).isFalse();
    assertThat(sink3.gotOutput).isTrue();
  }

  @Test
  public void testCancellationHandlersInvoked() {
    BlazeContext context = BlazeContext.create();

    Runnable handler1 = Mockito.mock(Runnable.class);
    Runnable handler2 = Mockito.mock(Runnable.class);
    context.addCancellationHandler(handler1);
    context.addCancellationHandler(handler2);

    context.setCancelled();
    verify(handler1, times(1)).run();
    verify(handler2, times(1)).run();
  }

  @Test
  public void testParentContextCancellationHandlersInvoked() {
    BlazeContext parentContext = BlazeContext.create();
    BlazeContext childContext = BlazeContext.create(parentContext);

    Runnable handler = Mockito.mock(Runnable.class);
    parentContext.addCancellationHandler(handler);

    childContext.setCancelled();
    verify(handler, times(1)).run();
  }

  @Test
  public void testCircularCancellationHandlersRunOnce() {
    BlazeContext context = BlazeContext.create();
    Runnable handler = Mockito.mock(Runnable.class);
    doNothing().doThrow(new RuntimeException("Handler called more than once")).when(handler).run();

    context.addCancellationHandler(handler);
    context.addCancellationHandler(context::setCancelled);
    context.setCancelled();

    verify(handler, times(1)).run();
    assertThat(context.isCancelled()).isTrue();
  }
}
