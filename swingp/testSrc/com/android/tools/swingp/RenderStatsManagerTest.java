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
package com.android.tools.swingp;

import static com.google.common.truth.Truth.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class RenderStatsManagerTest {
  @Test
  public void getsNullForEmptyOrDisabled() {
    // Ensure empty stats returns JsonNull.
    assertThat(RenderStatsManager.getJson()).isSameAs(JsonNull.INSTANCE);

    // Ensure JsonNull is returned when the system is disabled.
    MethodStat fakeStat = new TestMethodStat(this);
    fakeStat.endMethod();
    assertThat(RenderStatsManager.getJson()).isSameAs(JsonNull.INSTANCE);
  }

  @Test
  public void getsCorrectThreads() throws InterruptedException {
    RenderStatsManager.setIsEnabled(true);

    MethodStat fakeStat = new TestMethodStat(this);
    fakeStat.endMethod();

    String separateThreadName = "Separate Thread";
    Thread separateThread = new Thread(separateThreadName) {
      @Override
      public void run() {
        super.run();

        MethodStat separateStat = new TestMethodStat(this);
        separateStat.endMethod();
      }
    };
    separateThread.start();
    separateThread.join();

    JsonElement element = RenderStatsManager.getJson();
    assertThat(element).isNotSameAs(JsonNull.INSTANCE);
    assertThat(element.isJsonArray()).isTrue();

    JsonArray root = (JsonArray)element;
    assertThat(root.size()).isEqualTo(2);

    assertThat(root.get(0).isJsonObject()).isTrue();
    JsonElement currentThreadElement = ((JsonObject)root.get(0)).get("threadName");
    assertThat(currentThreadElement.isJsonPrimitive()).isTrue();

    assertThat(root.get(1).isJsonObject()).isTrue();
    JsonElement separateThreadElement = ((JsonObject)root.get(1)).get("threadName");
    assertThat(separateThreadElement.isJsonPrimitive()).isTrue();

    Set<String> threadNames = new HashSet<>();
    threadNames.add(currentThreadElement.getAsString());
    threadNames.add(separateThreadElement.getAsString());
    assertThat(threadNames).containsExactly(Thread.currentThread().getName(), separateThreadName);

    // Ensure there are no leftovers.
    assertThat(RenderStatsManager.getJson()).isSameAs(JsonNull.INSTANCE);

    RenderStatsManager.setIsEnabled(false);
  }

  /**
   * Trivial extension of {@link MethodStat} (since it's abstract) to test its implementation.
   */
  private static final class TestMethodStat extends MethodStat {
    public TestMethodStat(@NotNull Object owner) {
      super(owner);
    }
  }
}
