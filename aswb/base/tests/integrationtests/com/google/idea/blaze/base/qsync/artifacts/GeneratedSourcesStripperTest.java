package com.google.idea.blaze.base.qsync.artifacts;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.ByteSource;
import com.google.idea.testing.java.LightJavaCodeInsightFixtureTestCase4Concrete;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GeneratedSourcesStripperTest extends LightJavaCodeInsightFixtureTestCase4Concrete {

  GeneratedSourcesStripper gcs;

  @Before
  public void createGeneratedClassStripper() {
    gcs = new GeneratedSourcesStripper(getFixture().getProject());
  }

  @Test
  public void getGeneratedValue_fully_qualified() throws Exception {
    String content =
        String.join(
            "\n",
            "package com.test;",
            "",
            "import com.otherpackage.SomeOtherClass;",
            "",
            "@javax.annotation.processing.Generated(\"com.test.processor.MyProcessor\")",
            "public final class GeneratedClass extends SomeOtherClass {",
            "  public GeneratedClass() {}",
            "}");
    assertThat(
            gcs.getGeneratedValue(
                "SomeOtherClass.java", ByteSource.wrap(content.getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.test.processor.MyProcessor");
  }

  @Test
  public void getGeneratedValue_not_fully_qualified() throws Exception {
    String content =
        String.join(
            "\n",
            "package com.test;",
            "",
            "import com.otherpackage.SomeOtherClass;",
            "import javax.annotation.processing.Generated;",
            "",
            "@Generated(\"com.test.processor.MyProcessor\")",
            "public final class GeneratedClass extends SomeOtherClass {",
            "  public GeneratedClass() {}",
            "}");
    assertThat(
            gcs.getGeneratedValue(
                "SomeOtherClass.java", ByteSource.wrap(content.getBytes(StandardCharsets.UTF_8))))
        .isEqualTo("com.test.processor.MyProcessor");
  }

  @Test
  public void getGeneratedValue_other_annotation_ignored() throws Exception {
    String content =
        String.join(
            "\n",
            "package com.test;",
            "",
            "import com.otherpackage.SomeOtherClass;",
            "import com.otherpackage.Generated;",
            "",
            "@Generated(\"com.test.processor.MyProcessor\")",
            "public final class GeneratedClass extends SomeOtherClass {",
            "  public GeneratedClass() {}",
            "}");
    assertThat(
            gcs.getGeneratedValue(
                "SomeOtherClass.java", ByteSource.wrap(content.getBytes(StandardCharsets.UTF_8))))
        .isNull();
  }
}
