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
package com.android.tools.idea.observable;

import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.rules.ExternalResource;

/**
 * {@link Rule} which overrides the default {@link BatchInvoker.Strategy} for all tests in the
 * current test class, in a way where one can't forget to clear it later.
 *
 * <pre>
 *   public class MyTest {
 *     private TestInvokeStrategy myStrategy = new TestInvokeStrategy();
 *     @Rule
 *     public BatchInvokerStrategyRule myStrategyRule = new BatchInvokerStrategyRule(myStrategy);
 *
 *     @Test
 *     public void testPropertyCode() throws Exception {
 *       ...
 *       someProperty.set(123);
 *       someOtherProperty.set(456);
 *       myStrategy.updateOneStep();
 *       assertThat(...);
 *       ...
 *     }
 *   }
 * </pre>
 */
public final class BatchInvokerStrategyRule extends ExternalResource {
  @NotNull private final BatchInvoker.Strategy myStrategy;

  public BatchInvokerStrategyRule(@NotNull BatchInvoker.Strategy strategy) {
    myStrategy = strategy;
  }

  @Override
  protected void before() {
    BatchInvoker.setOverrideStrategy(myStrategy);
  }

  @Override
  protected void after() {
    BatchInvoker.clearOverrideStrategy();
  }
}
