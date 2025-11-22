/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.studio.samples;

import static com.google.common.truth.Truth.assertThat;

import com.appspot.gsamplesindex.samplesindex.model.Sample;
import com.appspot.gsamplesindex.samplesindex.model.SampleCollection;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link com.google.gct.studio.samples.SampleImportTreeManager}
 */
@RunsInEdt
public class SampleImportTreeManagerTest {

  @Rule
  public EdtRule edtRule = new EdtRule();

  SampleImportTreeManager mySampleManager;
  final List<Sample> mySamples = new ArrayList<>(3);

  @Before
  public void setUp() {
    initSamples();
    mySampleManager = new SampleImportTreeManager(new Tree(), new SampleCollection().setItems(mySamples));
  }

  @Test
  public void testFormatName_emptyFormat() {
    assertThat(SampleImportTreeManager.formatName("  ")).isEqualTo("Unnamed");
    assertThat(SampleImportTreeManager.formatName("").equals("Unnamed"));
    assertThat(SampleImportTreeManager.formatName(null).equals("Unnamed"));
  }

  @Test
  public void testFormatName_format() {
    assertThat(SampleImportTreeManager.formatName("GooseMoose").equals("Goose Moose"));
    assertThat(SampleImportTreeManager.formatName("goosemoose").equals("goosemoose"));
    assertThat(SampleImportTreeManager.formatName("GooseMoose-Juice").equals("Goose Moose - Juice"));
    assertThat(SampleImportTreeManager.formatName("GooseMoose - Juice").equals("Goose Moose - Juice"));
    assertThat(SampleImportTreeManager.formatName("Goose        Moose  - Juice         ").equals("Goose Moose - Juice"));
    assertThat(SampleImportTreeManager.formatName("TV Input Framework (TIF)").equals("TV Input Framework (TIF)"));
    assertThat(SampleImportTreeManager.formatName("(TIF)").equals("(TIF)"));
  }

  @Test
  public void testFilterSamples_emptySearch() {
    doFilterCheck("", 3);
    doFilterCheck("          ", 3);
  }

  @Test
  public void testFilterSamples_singleWord() {
    doFilterCheck("Test", 3);
    doFilterCheck("goose", 1);
    doFilterCheck("moose", 1);
    doFilterCheck("Sample", 3);
    doFilterCheck("Geese", 1);
    doFilterCheck("Animal", 2);
    doFilterCheck("Roof", 1);
    doFilterCheck("Tomato", 0);
    doFilterCheck("ani", 2);
    doFilterCheck("z", 0);
    doFilterCheck("o", 3);
  }

  @Test
  public void testFilterSamples_twoWords() {
    doFilterCheck("Test Sample", 3);
    doFilterCheck("Animal Thing", 0);
    doFilterCheck("sample Moose", 1);
    doFilterCheck("Moose Goose", 0);
    doFilterCheck("ant moo", 1);
    doFilterCheck("house Roof", 1);
    doFilterCheck("Test Goose", 1);
    doFilterCheck("Tomato Antlers", 0);
  }

  @Test
  public void testFilterSamples_lotsOfWords() {
    doFilterCheck("Test Sample of", 3);
    doFilterCheck("Moose Goose House", 0);
    doFilterCheck("Sample Moose Antlers", 1);
    doFilterCheck("Goose goose Goose", 1);
    doFilterCheck("Antlers Moose Tomato", 0);
    doFilterCheck("oose Animal of", 2);
  }


  private void doFilterCheck(@NotNull String query, int resultSize) {
    Set<Sample> result = mySampleManager.filterSamples(query);
    assertThat(result.size()).isEqualTo(resultSize);
  }

  private void initSamples() {
    mySamples.add(createSample("Test Goose", "A Sample of Geese", Arrays.asList("Animal", "Bird")));
    mySamples.add(createSample("TestMoose", "A Sample of Moose", Arrays.asList("Animal", "Antlers")));
    mySamples.add(createSample("Test House", "A Worrying Sample of Houses", Arrays.asList("Thing", "Roof")));
  }

  private static Sample createSample(String title, String description, List<String> categories) {
    return new Sample().setTitle(title).setDescription(description).setCategories(categories);
  }
}
