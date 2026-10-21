/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.rendering.classloading

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirewalledResourcesClassLoaderTest {

  @Test
  fun `test class resources are found and other resources are firewalled`() {
    val parentClassLoader = this::class.java.classLoader
    val classLoader = FirewalledResourcesClassLoader(parentClassLoader)

    // The .class file for this test itself should be accessible
    val testClassName = FirewalledResourcesClassLoaderTest::class.java.name.replace('.', '/') + ".class"
    assertNotNull("Should find .class resource", classLoader.getResource(testClassName))
    assertTrue("Should find .class resources", classLoader.getResources(testClassName).hasMoreElements())
    assertNotNull("Should find .class resource stream", classLoader.getResourceAsStream(testClassName))

    // Other resources (e.g., non-existent text file, or actual assets if any) should return null/empty
    val nonClassResource = "META-INF/MANIFEST.MF" // This might exist, but should be firewalled
    assertNull("Should NOT find non-class resource", classLoader.getResource(nonClassResource))
    assertTrue("Should return empty for non-class resources", !classLoader.getResources(nonClassResource).hasMoreElements())
    assertNull("Should NOT find non-class resource stream", classLoader.getResourceAsStream(nonClassResource))
  }
}
