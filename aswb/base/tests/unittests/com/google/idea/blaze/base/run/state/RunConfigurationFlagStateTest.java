/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.state;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState.RunConfigurationFlagsStateEditor;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RunConfigurationFlagsState}. */
@RunWith(JUnit4.class)
public class RunConfigurationFlagStateTest {
  @Test
  public void testEscapedDoubleQuotesRetainedAfterReserialization() {
    // previously, we were removing escape chars and quotes during ParametersListUtil.parse, then
    // not putting them back when converting back to a string.
    ImmutableList<String> flags = ImmutableList.of("--flag=\\\"Hello_world!\\\"", "--flag2");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(flags);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly("--flag=\"Hello_world!\"", "--flag2")
        .inOrder();
  }

  @Test
  public void testEscapedSingleQuotesRetainedAfterReserialization() {
    ImmutableList<String> flags = ImmutableList.of("--flag=\\'Hello_world!\\'", "--flag2");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(flags);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly("--flag='Hello_world!'", "--flag2")
        .inOrder();
  }

  @Test
  public void testQuotesRetainedAfterReserialization() {
    ImmutableList<String> flags = ImmutableList.of("\"--flag=test\"");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(flags);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses()).containsExactly("--flag=test");
  }

  @Test
  public void testDoubleQuotesInEditor() {
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    RunConfigurationStateEditor editor = state.getEditor(null);
    JTextArea textArea = getTextField(editor);

    String originalText = "\"--flags=a b\"\n\"--flags=\\\"a b\\\"\"";
    textArea.setText(originalText);
    ImmutableList<String> expectedRawFlags =
        ImmutableList.of("\"--flags=a b\"", "\"--flags=\\\"a b\\\"\"");

    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedRawFlags);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(originalText);
    // test round trip is stable
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedRawFlags);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(originalText);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly("--flags=a b", "--flags=\"a b\"")
        .inOrder();
  }

  @Test
  public void testSingleQuotesInEditor() {
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    RunConfigurationStateEditor editor = state.getEditor(null);
    JTextArea textArea = getTextField(editor);

    String originalText = "'--flags=a b'\n'--flags=\"a b\"'";
    textArea.setText(originalText);
    ImmutableList<String> expectedRawFlags = ImmutableList.of("'--flags=a b'", "'--flags=\"a b\"'");

    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedRawFlags);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(originalText);
    // test round trip is stable
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedRawFlags);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(originalText);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly("--flags=a b", "--flags=\"a b\"")
        .inOrder();
  }

  @Test
  public void testNestedQuotesRetainedAfterRoundTripSerialization() {
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    RunConfigurationStateEditor editor = state.getEditor(null);
    JTextArea textArea = getTextField(editor);

    String originalText = "--where_clause=\"op = 'addshardreplica' AND purpose = 'rebalancing'\"";
    textArea.setText(originalText);
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).containsExactly(originalText);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(originalText);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly("--where_clause=op = 'addshardreplica' AND purpose = 'rebalancing'");
  }

  @Test
  public void testSplitOnWhitespaceAndNewlines() {
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    RunConfigurationStateEditor editor = state.getEditor(null);
    JTextArea textArea = getTextField(editor);

    String originalText = "--flag=a --other=b --c='d=e'\n\"--final=f\"";
    List<String> expectedFlags =
        ImmutableList.of("--flag=a", "--other=b", "--c='d=e'", "\"--final=f\"");

    textArea.setText(originalText);
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedFlags);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(Joiner.on("\n").join(expectedFlags));
    // test round trip is stable
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedFlags);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly("--flag=a", "--other=b", "--c=d=e", "--final=f")
        .inOrder();
  }

  @Test
  public void testFlagsContainingQuotedNewlines() {
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    RunConfigurationStateEditor editor = state.getEditor(null);
    JTextArea textArea = getTextField(editor);

    String originalText = "\"a\nb\nc\"";
    List<String> expectedFlags = ImmutableList.of("\"a\nb\nc\"");

    textArea.setText(originalText);
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedFlags);
    editor.resetEditorFrom(state);
    assertThat(textArea.getText()).isEqualTo(originalText);
    // test round trip is stable
    editor.applyEditorTo(state);
    assertThat(state.getRawFlags()).isEqualTo(expectedFlags);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses()).containsExactly("a\nb\nc");
  }

  @Test
  public void testNormalFlagsAreNotMangled() {
    ImmutableList<String> flags =
        ImmutableList.of(
            "--test_sharding_strategy=disabled",
            "--test_strategy=local",
            "--experimental_show_artifacts",
            "--test_filter=com.google.idea.blaze.base.run.state.RunConfigurationFlagStateTest#",
            "--define=ij_product=intellij-latest");
    RunConfigurationFlagsState state = new RunConfigurationFlagsState("tag", "field");
    state.setRawFlags(flags);

    RunConfigurationStateEditor editor = state.getEditor(null);
    editor.resetEditorFrom(state);
    editor.applyEditorTo(state);

    assertThat(state.getRawFlags()).isEqualTo(flags);
    // test flags generated for commands
    assertThat(state.getFlagsForExternalProcesses())
        .containsExactly(
            "--test_sharding_strategy=disabled",
            "--test_strategy=local",
            "--experimental_show_artifacts",
            "--test_filter=com.google.idea.blaze.base.run.state.RunConfigurationFlagStateTest#",
            "--define=ij_product=intellij-latest")
        .inOrder();
  }

  private static JTextArea getTextField(RunConfigurationStateEditor editor) {
    assertThat(editor).isInstanceOf(RunConfigurationFlagsStateEditor.class);
    RunConfigurationFlagsStateEditor flagsEditor = (RunConfigurationFlagsStateEditor) editor;
    JComponent internalField = flagsEditor.getInternalComponent();
    assertThat(internalField).isInstanceOf(JTextArea.class);
    return (JTextArea) internalField;
  }
}
