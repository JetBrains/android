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
package com.android.tools.idea.tests.gui.framework.matcher;

import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.awt.*;

/**
 * Convenience wrapper around {@link GenericTypeMatcher} for chaining and modifying matchers.
 */
public abstract class FluentMatcher<T extends Component> extends GenericTypeMatcher<T> {
  public FluentMatcher(@NotNull Class<T> supportedType) {
    super(supportedType);
  }

  public static <T extends Component> FluentMatcher<T> wrap(GenericTypeMatcher<T> matcher) {
    return new FluentMatcher<T>(matcher.supportedType()) {
      @Override
      protected boolean isMatching(@Nonnull T component) {
        return matcher.matches(component);
      }
    };
  }

  public FluentMatcher<T> and(@NotNull GenericTypeMatcher<? extends T> other) {
    return new FluentMatcher<T>(supportedType()) {
      @Override
      protected boolean isMatching(@NotNull T component) {
        return FluentMatcher.this.isMatching(component) && other.matches(component);
      }
    };
  }

  /**
   * By default, negate() has to return a Component-typed Matcher, since the check that the component type maches the given type is done
   * before isMatching is even called, and so it's impossible to reverse it.
   */
  public FluentMatcher<Component> negate() {
    return new FluentMatcher<Component>(Component.class) {
      @Override
      protected boolean isMatching(@NotNull Component component) {
        return !FluentMatcher.this.matches(component);
      }
    };
  }

  /**
   * Inverts this condition, returning a matcher of the given type.

   * @param type The type of component matched by the resulting Matcher
   * @return In effect a matcher for components of type {@code R} that {@code this} does not match (including the type check for {@code T}).
   */
  public <R extends Component> FluentMatcher<R> negate(Class<R> type) {
    return new FluentMatcher<R>(type) {
      @Override
      protected boolean isMatching(@NotNull R component) {
        return !FluentMatcher.this.matches(component);
      }
    };
  }

  public FluentMatcher<T> andIsShowing() {
    return and(new GenericTypeMatcher<T>(supportedType(), true) {
      @Override
      protected boolean isMatching(@Nonnull T component) {
        return true;
      }
    });
  }

  public FluentMatcher<T> andIsEnabled() {
    return and(new FluentMatcher<T>(supportedType()) {
      @Override
      protected boolean isMatching(@Nonnull T component) {
        return component.isEnabled();
      }
    });
  }
}
