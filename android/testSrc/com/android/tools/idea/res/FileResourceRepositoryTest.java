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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.google.common.io.Files;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.AndroidGradleModel.EXPLODED_AAR;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;
import static java.io.File.separatorChar;

public class FileResourceRepositoryTest extends TestCase {

  public void testCacheUseSoftReferences() throws IOException {
    File dir = Files.createTempDir();
    try {
      assertNotNull(FileResourceRepository.get(dir));
      // We shouldn't clear it out immediately on GC *eligibility*:
      System.gc();
      assertNotNull(FileResourceRepository.getCached(dir));
      // However, in low memory conditions we should:
      try {
        PlatformTestUtil.tryGcSoftlyReachableObjects();
      } catch (Throwable t) {
        // The above method can throw java.lang.OutOfMemoryError; that's fine for this test
      }
      System.gc();
      assertNull(FileResourceRepository.getCached(dir));
    }
    finally {
      FileUtil.delete(dir);
    }
  }

  public void testGetAllDeclaredIds() throws IOException {
    FileResourceRepository repository = getTestRepository();
    assertSameElements(repository.getAllDeclaredIds(), "id1", "id2", "id3");
  }

  public void testMultipleValues() throws IOException {
    FileResourceRepository repository = getTestRepository();
    List<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, "hello");
    assertNotNull(items);
    List<String> helloVariants = ContainerUtil.map(
      items,
      new Function<ResourceItem, String>() {
        @Override
        public String fun(ResourceItem resourceItem) {
          ResourceValue value = resourceItem.getResourceValue(false);
          assertNotNull(value);
          return value.getValue();
        }
    });
    assertSameElements(helloVariants, "bonjour", "hello", "hola");
  }

  @NotNull
  static FileResourceRepository getTestRepository() throws IOException {
    String aarPath = AndroidTestBase.getTestDataPath() + separatorChar +
                     "rendering" + separatorChar +
                     EXPLODED_AAR + separatorChar +
                     "my_aar_lib" + separatorChar +
                     "res";
    return FileResourceRepository.get(new File(aarPath));
  }
}
