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

package com.android.tools.idea.rendering.tokens;

import static com.google.idea.testing.runfiles.Runfiles.runfilesPath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BazelClassFileFinderTest {
  @Test
  public void findClassFile() {
    // Arrange
    var finder = new BazelClassFileFinder(
      List.of(runfilesPath("tools/adt/idea/aswb/aswb/tests/unittests/com/android/tools/idea/rendering/tokens/libhelloworld.jar")));

    // Act
    var content = finder.findClassFile("com.android.tools.idea.rendering.tokens.HelloWorld");

    // Assert
    assertNotNull(content);

    var c = new ByteArrayClassLoader().defineClass(content.getContent());
    assertEquals("com.android.tools.idea.rendering.tokens.HelloWorld", c.getName());
  }

  @Test
  public void findClassFileClassMapsToMultipleJars() throws Exception {
    // Arrange
    var finder = new BazelClassFileFinder(
      List.of(runfilesPath("tools/adt/idea/aswb/aswb/tests/unittests/com/android/tools/idea/rendering/tokens/libhelloworld.jar"),
              runfilesPath("tools/adt/idea/aswb/aswb/tests/unittests2/com/android/tools/idea/rendering/tokens/libhelloworld.jar")));

    // Act
    var content = finder.findClassFile("com.android.tools.idea.rendering.tokens.HelloWorld");

    // Assert
    assertNotNull(content);

    var c = new ByteArrayClassLoader().defineClass(content.getContent());
    assertEquals("Hello, World 1!", c.getDeclaredMethod("getHelloWorld").invoke(null));
  }

  private static final class ByteArrayClassLoader extends ClassLoader {
    private Class<?> defineClass(byte[] c) {
      return defineClass(null, c, 0, c.length);
    }
  }
}
