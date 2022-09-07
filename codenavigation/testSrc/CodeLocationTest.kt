import com.android.tools.idea.codenavigation.CodeLocation
import com.google.common.truth.Truth.assertThat
import org.junit.Ignore
import org.junit.Test

class CodeLocationTest {
  @Test
  fun extractOuterClassFromSimpleClass() {
    val location = CodeLocation.Builder("outerClass").build()
    assertThat(location.outerClass).isEqualTo("outerClass")
  }

  @Test
  fun extractOuterClassFromInnerClass() {
    val location = CodeLocation.Builder("outerClass\$innerClass").build()
    assertThat(location.outerClass).isEqualTo("outerClass")
  }

  @Test
  fun extractOuterClassFromAnonymousClass() {
    val location = CodeLocation.Builder("outerClass\$innerClass\$1").build()
    assertThat(location.outerClass).isEqualTo("outerClass")
  }

  @Test
  fun equalityOfSameInstance() {
    val location = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()
    assertThat(location).isEqualTo(location)
  }

  @Test
  fun equalityOfSameValue() {
    val locationA = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()

    val locationB = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()

    assertThat(locationA).isEqualTo(locationB)
    assertThat(locationB).isEqualTo(locationA)
  }

  @Ignore
  @Test
  fun equalityOfDifferentValue() {
    val locationA = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()

    val locationB = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "double"))
      .build()

    assertThat(locationA).isNotEqualTo(locationB)
    assertThat(locationB).isNotEqualTo(locationA)
  }

  @Test
  fun hashOfSameInstance() {
    val location = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()
    assertThat(location.hashCode()).isEqualTo(location.hashCode())
  }

  @Test
  fun hashOfSameValue() {
    val locationA = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()

    val locationB = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()

    assertThat(locationA.hashCode()).isEqualTo(locationB.hashCode())
    assertThat(locationB.hashCode()).isEqualTo(locationA.hashCode())
  }

  @Ignore
  @Test
  fun hashOfDifferentValue() {
    val locationA = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "int", "double"))
      .build()

    val locationB = CodeLocation.Builder("outerClass")
      .setLineNumber(10)
      .setMethodParameters(listOf("int", "double"))
      .build()

    assertThat(locationA.hashCode()).isNotEqualTo(locationB.hashCode())
    assertThat(locationB.hashCode()).isNotEqualTo(locationA.hashCode())
  }
}