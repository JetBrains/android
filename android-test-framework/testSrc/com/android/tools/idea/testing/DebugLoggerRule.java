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
package com.android.tools.idea.testing;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.TestApplicationManager;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.ExternalResource;

/**
 * JUnit4 rule that enables debug logging from the default {@link Logger}.
 * Use when running basic unit tests that rely on the default {@link Logger}
 * implementation, which is limited to a no-op for {@link Logger#debug} and
 * {@link Logger#trace} calls.
 *
 * Intended use:
 * <pre>
 * public class MyTest {
 *  &#064;ClassRule
 *  public static DebugLoggerRule ourDebugLoggerRule = new DebugLoggerRule();
 *
 *  // Tests that depend on Logger.debug() to output messages to {@link System#out}
 * }
 * </pre>
 */
public class DebugLoggerRule extends ExternalResource {
  @Nullable private Logger.Factory myOriginalFactory;
  private final Class<? extends Logger.Factory> myFactory;

  public DebugLoggerRule() {
    this(MyDebugLoggerFactory.class);
  }

  public DebugLoggerRule(Class<? extends Logger.Factory> factoryClass) {
    myFactory = factoryClass;
  }

  @Override
  protected void before() throws Throwable {
    super.before();

    // Initialize TestApplicationManager.Companion before setting the logger factory, since
    // TestApplicationManager.Companion sets the logger factory on init and we want to be sure
    // it's overridden here.
    //noinspection ResultOfMethodCallIgnored
    TestApplicationManager.Companion.getInstanceIfCreated();
    try {
      Field factoryField = Logger.class.getDeclaredField("ourFactory");
      factoryField.setAccessible(true);
      myOriginalFactory = (Logger.Factory)factoryField.get(null);
      factoryField.setAccessible(false);
    }
    catch (IllegalAccessException | NoSuchFieldException e) {
      System.err.println("Error injecting custom logger factory: " + e);
    }
    Logger.setFactory(myFactory);
  }

  @Override
  protected void after() {
    super.after();
    if (myOriginalFactory != null) {
      Logger.setFactory(myOriginalFactory.getClass());
    }
  }

  private static class MyDebugLoggerFactory implements Logger.Factory {
    @NotNull
    @Override
    public Logger getLoggerInstance(@NotNull String category) {
      return new DefaultLogger(category) {
        @Override
        public boolean isDebugEnabled() {
          return true;
        }

        @Override
        public boolean isTraceEnabled() {
          return false;
        }

        @Override
        public void trace(String message) {
        }

        @Override
        public void debug(String message, @Nullable Throwable t) {
          System.out.println(message);
          if (t != null) {
            t.printStackTrace(System.out);
          }
        }

        @Override
        public void info(String message, Throwable t) {
          debug(message, t);
        }
      };
    }
  }
}
