package com.android.tools.idea.flags.overrides;

import static com.google.common.truth.Truth.assertThat;

import java.util.Optional;
import java.io.UnsupportedEncodingException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import com.android.flags.Flag;
import com.android.flags.Flags;
import com.android.flags.FlagGroup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BuildSettingFlagOverridesTest {
  private final Flags flags = new Flags();

  @Test
  public void overrides_agpFlag() throws Exception {
    FlagGroup gradleGroup = new FlagGroup(flags, "gradle.ide", "Gradle Project System");
    Flag<String> agpVersionFlag = Flag.create(
      gradleGroup, "agp.version.to.use", "Version of AGP to use", "description", "");

    Optional<InputStream> inputStream = asInputStream("gradle.ide.agp.version.to.use=8.0.0");
    BuildSettingFlagOverrides flagOverrides = BuildSettingFlagOverrides.create(inputStream);

    assertThat(flagOverrides.get(agpVersionFlag)).isEqualTo("8.0.0");
  }

  @Test
  public void overrides_flagNotPresent() throws Exception {
    FlagGroup flagGroup = new FlagGroup(flags, "com.google", "test flag group");
    Flag<String> flag = Flag.create(flagGroup, "foo.bar", "summary", "description", "");

    Optional<InputStream> inputStream = asInputStream("gradle.ide.agp.version.to.use=8.0.0");
    BuildSettingFlagOverrides flagOverrides = BuildSettingFlagOverrides.create(inputStream);

    assertThat(flagOverrides.get(flag)).isNull();
  }

  private Optional<InputStream> asInputStream(String flags) throws UnsupportedEncodingException {
    return Optional.of(new ByteArrayInputStream(flags.getBytes("UTF-8")));
  }
}