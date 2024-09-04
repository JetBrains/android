/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async.process;

import static com.google.common.base.StandardSystemProperty.JAVA_IO_TMPDIR;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.process.ExternalTask.ExternalTaskImpl;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExternalTaskImpl}. */
@RunWith(JUnit4.class)
public final class ExternalTaskImplTest extends BlazeTestCase {

  private static File newTempFile() throws IOException {
    File temp = File.createTempFile("sadjfhjk-", "-sodiuflk");
    temp.deleteOnExit();
    temp.setExecutable(true);
    return temp;
  }

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    registerExtensionPoint(BinaryPathRemapper.EP_NAME, BinaryPathRemapper.class);
  }

  @Test
  public void getCustomBinary_withoutCustomPath() throws Exception {
    System.clearProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY);
    assertThat(ExternalTaskImpl.getCustomBinary("sh")).isNull();
  }

  @Test
  public void getCustomBinary_multiArgCommand() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, JAVA_IO_TMPDIR.value());
    File temp = newTempFile();
    assertThat(ExternalTaskImpl.getCustomBinary(temp.getName() + " --withlog")).isNull();
  }

  @Test
  public void getCustomBinary_fullPathCommand() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, JAVA_IO_TMPDIR.value());
    File temp = newTempFile();
    assertThat(ExternalTaskImpl.getCustomBinary(temp.getAbsolutePath())).isNull();
  }

  @Test
  public void getCustomBinary_nonExistent() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, JAVA_IO_TMPDIR.value());
    assertThat(ExternalTaskImpl.getCustomBinary("this_is_almost_certainly_not_an_existing_file"))
        .isNull();
  }

  @Test
  public void getCustomBinary_directory() throws Exception {
    File tmpDir = new File(JAVA_IO_TMPDIR.value());
    assertThat(tmpDir.exists()).isTrue();
    assertThat(tmpDir.isFile()).isFalse();
    System.setProperty(
        ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, tmpDir.getParentFile().getAbsolutePath());
    assertThat(ExternalTaskImpl.getCustomBinary(tmpDir.getName())).isNull();
  }

  @Test
  public void getCustomBinary_success() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, JAVA_IO_TMPDIR.value());
    File temp = newTempFile();
    File file = ExternalTaskImpl.getCustomBinary(temp.getName());
    assertThat(file.getAbsolutePath()).isEqualTo(temp.getAbsolutePath());
  }

  @Test
  public void resolveCustomBinary_success() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, JAVA_IO_TMPDIR.value());
    File temp = newTempFile();
    assertThat(
            ExternalTaskImpl.resolveCustomBinary(ImmutableList.of(temp.getName(), "--some_flag")))
        .containsExactly(temp.getAbsolutePath(), "--some_flag")
        .inOrder();
  }

  @Test
  public void resolveCustomBinary_unmodified() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, JAVA_IO_TMPDIR.value());
    String notFoundBinName = "probably_not_a_binary_that_exists_in_the_temp_directory";
    assertThat(
            ExternalTaskImpl.resolveCustomBinary(ImmutableList.of(notFoundBinName, "--some_flag")))
        .containsExactly(notFoundBinName, "--some_flag")
        .inOrder();
  }
}
