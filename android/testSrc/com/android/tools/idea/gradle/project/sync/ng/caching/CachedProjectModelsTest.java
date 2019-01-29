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
package com.android.tools.idea.gradle.project.sync.ng.caching;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.Future;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Tests for {@link CachedProjectModels}.
 */
public class CachedProjectModelsTest extends IdeaTestCase {
  private CachedProjectModels myCache;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myCache = new CachedProjectModels();
  }

  public void testSaveToDisk() throws Exception {
    CachedModuleModels module1 = myCache.addModule(createModule("module1"));
    Person p1 = new Person("Luke");
    module1.addModel(p1);

    CachedModuleModels module2 = myCache.addModule(createModule("module2"));
    Person p2 = new Person("Leia");
    module2.addModel(p2);

    Project project = getProject();
    Future<?> future = myCache.saveToDisk(project);
    future.get(10, SECONDS);

    File cacheFilePath = CachedProjectModels.getCacheFilePath(project);
    assertAbout(file()).that(cacheFilePath).isFile();

    CachedProjectModels deserialized = deserialize(cacheFilePath);
    assertEquals(myCache, deserialized);
    assertThat(deserialized).isNotSameAs(myCache);

    CachedModuleModels deserializedModule1 = deserialized.findCacheForModule("module1");
    Person deserializedP1 = deserializedModule1.findModel(Person.class);
    assertEquals(p1, deserializedP1);

    CachedModuleModels deserializedModule2 = deserialized.findCacheForModule("module2");
    Person deserializedP2 = deserializedModule2.findModel(Person.class);
    assertEquals(p2, deserializedP2);
  }

  @NotNull
  private static CachedProjectModels deserialize(@NotNull File path) throws Exception {
    try (FileInputStream fis = new FileInputStream(path)) {
      try (ObjectInputStream ois = new ObjectInputStream(fis)) {
        return (CachedProjectModels)ois.readObject();
      }
    }
  }

  public static class Person implements Serializable {
    private String myName;

    public Person(@NotNull String name) {
      myName = name;
    }

    public String getName() {
      return myName;
    }

    public void setName(String name) {
      myName = name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Person)) {
        return false;
      }
      Person person = (Person)o;
      return Objects.equals(myName, person.myName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName);
    }
  }
}