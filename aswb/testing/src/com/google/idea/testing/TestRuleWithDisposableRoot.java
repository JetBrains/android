/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A base class for test rules that relies on intelliJ's {@link Disposer} mechanism to tear down
 * applied configuration rather than a {@code tearDown()} method. This is often a requirement when
 * relying on IntelliJ's test framework fixtures.
 */
public abstract class TestRuleWithDisposableRoot implements TestRule {

  protected abstract void before(Disposable disposable);

  public Statement apply(Statement base, Description description) {

    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        Disposable disposable = Disposer.newDisposable();
        before(disposable);
        try {
          base.evaluate();
        } finally {
          Disposer.dispose(disposable);
        }
      }
    };
  }
}
