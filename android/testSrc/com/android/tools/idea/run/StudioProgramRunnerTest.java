/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.google.common.truth.Truth;
import com.intellij.execution.ui.RunContentDescriptor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.Test;

public class StudioProgramRunnerTest {
  /**
   * {@link StudioProgramRunner.HiddenRunContentDescriptor} is a almost-pure wrapper class for
   * {@link RunContentDescriptor}, with the exception of the {@link RunContentDescriptor#isHiddenContent()} method overridden to return
   * {@code false}. All other methods in the wrapper class should be overrides to the base class (with the addition of
   * {@link com.intellij.openapi.Disposable} handling.
   *
   * This test is to ensure that all public and protected methods of the base class are overridden by the deriving class, and should break
   * if the base class has methods added to it due to IJ merges (in which case, just override the newly added method with proper disposal
   * handling). All other cases should result in compiler errors (either stale {@link Override} or mismatched signatures).
   */
  @Test
  public void ensureAllPublicProtectedMethodsAreOverridden() {
    long runContentDescriptorMethodCount = Arrays.stream(RunContentDescriptor.class.getDeclaredMethods())
      .filter(method -> {
        int modifier = method.getModifiers();
        return Modifier.isPublic(modifier) || Modifier.isProtected(modifier);
      })
      .count();
    long hiddenRunContentDescriptorMethodCount =
      Arrays.stream(StudioProgramRunner.HiddenRunContentDescriptor.class.getDeclaredMethods())
        .filter(method -> {
          int modifier = method.getModifiers();
          return Modifier.isPublic(modifier) || Modifier.isProtected(modifier);
        })
        .count();
    Truth.assertThat(runContentDescriptorMethodCount).isEqualTo(hiddenRunContentDescriptorMethodCount);
  }
}
