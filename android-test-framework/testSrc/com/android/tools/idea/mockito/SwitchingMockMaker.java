// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.mockito;

import org.jetbrains.annotations.Nullable;
import org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker;
import org.mockito.internal.creation.bytebuddy.InlineByteBuddyMockMaker;
import org.mockito.internal.util.concurrent.WeakConcurrentMap;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockMaker;

/**
 * This class is a plugin for Mockito framework.
 * <p>
 * It provides {@linkplain MockMaker} implementation capable to use several {@linkplain MockMaker}s
 * (e.g. inline and non-inline) in the same test. By default {@linkplain SwitchingMockMaker} prefers {@linkplain ByteBuddyMockMaker}, and
 * uses {@linkplain InlineByteBuddyMockMaker} to create mocks only if the default {@linkplain ByteBuddyMockMaker} reports that it cannot
 * mock the class.
 * <p>
 * {@linkplain InlineByteBuddyMockMaker} injects JVM agent to enable instrumentation, therefore {@linkplain SwitchingMockMaker} initializes
 * it lazily only when it is really needed. {@linkplain InlineByteBuddyMockMaker} will never be initialized, if the test code does not
 * exceed capabilities of the {@linkplain ByteBuddyMockMaker}.
 * <p>
 * Main goal of this plugin is to address performance issues introduced by the {@linkplain InlineByteBuddyMockMaker}.
 * {@linkplain ByteBuddyMockMaker} is fast, and works fine in most cases. It is not fair to get performance penalty for the majority of the
 * tests just because there are few tests in the same suite which need {@code inline} version. Developers should only pay for what they use.
 *
 * @see MockitoEx
 */
public class SwitchingMockMaker implements MockMaker {
  private final ByteBuddyMockMaker byteBuddy = new ByteBuddyMockMaker();

  private static class LazyInlineMockMaker {
    static InlineByteBuddyMockMaker INSTANCE = new InlineByteBuddyMockMaker();
  }

  private final WeakConcurrentMap<Object, MockMaker> mockToMaker = new WeakConcurrentMap.WithInlinedExpunction<>();

  @Override
  public <T> T createMock(MockCreationSettings<T> settings, MockHandler handler) {
    MockMaker makerToUse;
    makerToUse = selectMakerForType(settings.getTypeToMock());
    T mock = makerToUse.createMock(settings, handler);
    mockToMaker.put(mock, makerToUse);
    return mock;
  }

  private <T> MockMaker selectMakerForType(Class<T> typeToMock) {
    MockMaker makerToUse;
    if (MockitoEx.forceInlineMockMaker || !byteBuddy.isTypeMockable(typeToMock).mockable()) {
      makerToUse = LazyInlineMockMaker.INSTANCE;
    }
    else {
      makerToUse = byteBuddy;
    }
    return makerToUse;
  }

  @Nullable
  private MockMaker getMakerForMock(Object mock) {
    return mockToMaker.get(mock);
  }

  @Override
  public MockHandler getHandler(Object mock) {
    MockMaker maker = getMakerForMock(mock);
    if (maker != null) {
      return maker.getHandler(mock);
    }
    else {
      return null;
    }
  }


  @Override
  public void resetMock(Object mock, MockHandler newHandler, MockCreationSettings settings) {
    MockMaker maker = getMakerForMock(mock);
    if (maker != null) {
      maker.resetMock(mock, newHandler, settings);
    }
  }

  @Override
  public TypeMockability isTypeMockable(Class<?> type) {
    TypeMockability defaultAnswer = byteBuddy.isTypeMockable(type);
    if (!defaultAnswer.mockable()) {
      // most likely, LazyInlineMockMaker.INSTANCE will answer "true". But we don't want to instantiate it unless really needed.
      return LazyInlineMockMaker.INSTANCE.isTypeMockable(type);
    }
    else {
      return defaultAnswer;
    }
  }
}
