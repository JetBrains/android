package com.android.tools.idea.flags.overrides;

import static com.google.common.truth.Truth8.assertThat;

import java.util.Optional;
import java.io.InputStream;
import com.google.common.annotations.VisibleForTesting;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildFlagsTest {

  @Test
  public void getResourceInputStream_returnsData() {
    assertThat(BuildFlags.getResourceInputStream()).isPresent();
  }

  @Test
  public void getResourceInputStream_returnsEmpty() {
    assertThat(BuildFlags.getResourceInputStream("foo")).isEmpty();
  }
}