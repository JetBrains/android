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
package com.android.tools.profilers.memory.adapters;

import static com.android.tools.profilers.memory.adapters.ClassDb.INVALID_CLASS_ID;
import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class ClassDbTest {

  @Test
  public void testDescendantClasses() {
    final int classId1 = 1;
    final int classId2 = 2;
    final int classId3 = 3;
    final int classId4 = 4;
    final int classId5 = 5;

    final String class1 = "Class1";
    final String class2 = "Class2";
    final String class3 = "Class3";
    final String class4 = "Class4";
    final String class5 = "Class5";

    // Create the follow class relationships
    // - class1
    // -- class2
    // --- class3
    // - class4
    // -- class5
    ClassDb db = new ClassDb();
    ClassDb.ClassEntry entry1 = db.registerClass(classId1, INVALID_CLASS_ID, class1);
    ClassDb.ClassEntry entry2 = db.registerClass(classId2, classId1, class2);
    ClassDb.ClassEntry entry3 = db.registerClass(classId3, classId2, class3);
    ClassDb.ClassEntry entry4 = db.registerClass(classId4, INVALID_CLASS_ID, class4);
    ClassDb.ClassEntry entry5 = db.registerClass(classId5, classId4, class5);

    assertThat(db.getDescendantClasses(classId1)).containsExactly(entry1, entry2, entry3);
    assertThat(db.getDescendantClasses(classId2)).containsExactly(entry2, entry3);
    assertThat(db.getDescendantClasses(classId3)).containsExactly(entry3);
    assertThat(db.getDescendantClasses(classId4)).containsExactly(entry4, entry5);
    assertThat(db.getDescendantClasses(classId5)).containsExactly(entry5);
  }
}
