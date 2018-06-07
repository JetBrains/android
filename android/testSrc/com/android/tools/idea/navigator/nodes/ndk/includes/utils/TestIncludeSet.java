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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils;

import com.android.tools.idea.navigator.nodes.ndk.includes.RealWorldExamples;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class TestIncludeSet {

  @Test
  public void testExtractHeadersFromCompilerFlagsRealWorld() {
    for (String[] example : RealWorldExamples.COMPILER_INCLUDE_FLAGS) {
      ArrayList<String> array = new ArrayList<>();
      for (String flag : example) {
        array.add(flag);
      }
      IncludeSet includeSet = new IncludeSet();
      includeSet.addIncludesFromCompilerFlags(array, new File("."));
    }
  }

  @Test
  public void testExtractHeaderFoldersFromCompilerFlagsRealWorld() {
    for (String[] example : RealWorldExamples.COMPILER_INCLUDE_FLAGS) {
      ArrayList<String> array = new ArrayList<>();
      for (String flag : example) {
        array.add(flag);
      }
      new IncludeSet().addIncludesFromCompilerFlags(array, new File("/a/b/c/d/e/f/g/h/i/j/k"));
    }
  }

  @Test
  public void testOrderPreserved1() {
    ArrayList<String> example = new ArrayList<>();
    example.add("-I/folder2");
    example.add("-I/folder1");
    example.add("-I/folder1");
    IncludeSet includeSet = new IncludeSet();
    includeSet.addIncludesFromCompilerFlags(example, new File("."));
    List<File> extracted = includeSet.getIncludesInOrder();
    assertThat(extracted).hasSize(2);
    assertThat(extracted.get(0)).isEqualTo(new File("/folder2"));
    assertThat(extracted.get(1)).isEqualTo(new File("/folder1"));
  }

  @Test
  public void testOrderPreserved2() {
    ArrayList<String> example = new ArrayList<>();
    example.add("-I/folder1");
    example.add("-I/folder1");
    example.add("-I/folder2");
    IncludeSet includeSet = new IncludeSet();
    includeSet.addIncludesFromCompilerFlags(example, new File("."));
    List<File> extracted = includeSet.getIncludesInOrder();
    assertThat(extracted).hasSize(2);
    assertThat(extracted.get(0)).isEqualTo(new File("/folder1"));
    assertThat(extracted.get(1)).isEqualTo(new File("/folder2"));
  }

  @Test
  public void testOrderPreserved4() {
    ArrayList<String> example = new ArrayList<>();
    example.add("-I../../../folder1");
    example.add("-I../../../folder1");
    example.add("-I../../../folder2");
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(example, new File("/a/b/c/d/e"));
    assertThat(set.getIncludesInOrder()).hasSize(2);
    assertThat(set.getIncludesInOrder().get(0).getName()).isEqualTo("folder1");
    assertThat(set.getIncludesInOrder().get(1).getName()).isEqualTo("folder2");
  }

  @Test
  public void testOrderPreserved5() {
    ArrayList<String> example = new ArrayList<>();
    example.add("-I../../../folder2");
    example.add("-I../../../folder1");
    example.add("-I../../../folder1");
    IncludeSet set = new IncludeSet();
    set.addIncludesFromCompilerFlags(example, new File("/a/b/c/d/e"));
    assertThat(set.getIncludesInOrder()).hasSize(2);
    assertThat(set.getIncludesInOrder().get(0).getName()).isEqualTo("folder2");
    assertThat(set.getIncludesInOrder().get(1).getName()).isEqualTo("folder1");
  }

  @Test
  public void testOrderPreserved6() {
    ArrayList<String> example = new ArrayList<>();
    example.add("-sysroot=/folder2");
    example.add("--sysroot/folder1");
    example.add("--sysroot");
    example.add("/folder3");
    example.add("--sysroot/folder1");
    IncludeSet includeSet = new IncludeSet();
    includeSet.addIncludesFromCompilerFlags(example, new File("."));
    List<File> extracted = includeSet.getIncludesInOrder();
    assertThat(extracted).hasSize(3);
    assertThat(extracted.get(0)).isEqualTo(new File("/folder2/usr/include"));
    assertThat(extracted.get(1)).isEqualTo(new File("/folder1/usr/include"));
    assertThat(extracted.get(2)).isEqualTo(new File("/folder3/usr/include"));
  }

  @Test
  public void testGradleDoubleSlashWindowsRepro() {
    IncludeSet includeSet = new IncludeSet();
    includeSet.add("D:\\\\sub\\\\folder\\\\with\\\\includes", new File("."));
    List<File> extracted = includeSet.getIncludesInOrder();
    assertThat(extracted).hasSize(1);
    assertThat(extracted.get(0)).isEqualTo(new File("D:/sub/folder/with/includes"));
  }
}
