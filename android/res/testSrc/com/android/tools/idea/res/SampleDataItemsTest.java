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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.SampleDataResourceValue;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.truth.Truth;
import com.intellij.mock.MockPsiFile;
import com.intellij.mock.MockPsiManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Computable;
import com.intellij.testFramework.LightVirtualFile;
import java.io.IOException;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class SampleDataItemsTest {
  @Rule
  public AndroidProjectRule rule = AndroidProjectRule.inMemory();

  @NotNull
  private static SingleNamespaceResourceRepository getRepository() {
    SingleNamespaceResourceRepository repository = Mockito.mock(SingleNamespaceResourceRepository.class);
    Mockito.when(repository.getNamespace()).thenReturn(ResourceNamespace.RES_AUTO);
    return repository;
  }

  @Test
  public void testCsvParsing() {
    MockPsiFile file = ReadAction.compute(() -> new MockPsiFile(new LightVirtualFile("test.csv"), new MockPsiManager(rule.getProject())) {
      @Override
      @NotNull
      public String getName() {
        return "test.csv";
      }
    });
    file.putUserData(ModuleUtilCore.KEY_MODULE, rule.getModule());
    file.text = "header0,header1\nA1,B1\nA2\nA3,B3";

    SampleDataResourceItem[] item = ApplicationManager.getApplication()
      .runReadAction((Computable<SampleDataResourceItem[]>)() -> {
        try {
          return SampleDataResourceItem.getFromPsiFileSystemItem(getRepository(), file)
              .toArray(new SampleDataResourceItem[0]);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });

    Truth.assertThat(item).hasLength(2);

    Truth.assertThat(item[0].getName()).isEqualTo("test.csv/header0");
    SampleDataResourceValue value = (SampleDataResourceValue)item[0].getResourceValue();
    Truth.assertThat(value.getValueAsLines()).containsExactly("A1", "A2", "A3");

    Truth.assertThat(item[1].getName()).isEqualTo("test.csv/header1");
    value = (SampleDataResourceValue)item[1].getResourceValue();
    Truth.assertThat(value.getValueAsLines()).containsExactly("B1", "B3");
  }

  @Test
  public void testJsonParsing() {
    @Language("JSON")
    final String content = "{\"data\":[{\"animal\":\"cat\"},{\"animal\":\"dog\"}]}";

    MockPsiFile file = new MockPsiFile(new LightVirtualFile("test.json"), new MockPsiManager(rule.getProject())) {
      @Override
      @NotNull
      public String getName() {
        return "test.json";
      }
    };
    file.putUserData(ModuleUtilCore.KEY_MODULE, rule.getModule());
    file.text = content;

    SampleDataResourceItem[] item = ApplicationManager.getApplication()
      .runReadAction((Computable<SampleDataResourceItem[]>)() -> {
        try {
          return SampleDataResourceItem.getFromPsiFileSystemItem(getRepository(), file)
              .toArray(new SampleDataResourceItem[0]);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });

    Truth.assertThat(item).hasLength(1);


    Truth.assertThat(item[0].getName()).isEqualTo("test.json/data/animal");
    SampleDataResourceValue value = (SampleDataResourceValue)item[0].getResourceValue();
    Truth.assertThat(value.getValueAsLines()).containsExactly("cat", "dog");
  }
}
