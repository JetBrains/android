package com.android.tools.idea.lint.inspections

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInspection.JavaApiUsageInspection
import com.intellij.facet.FacetManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test

/** Test for [JavaApiUsageInspectionSuppressor]. */
class JavaApiUsageInspectionSuppressorTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun java9APIUsage() {
    val fixture = projectRule.fixture
    val module = projectRule.module

    // Set Java language level to 8.
    runWriteActionAndWait {
      val rootManager = ModuleRootManager.getInstance(module).modifiableModel
      val langLevelExt = rootManager.getModuleExtension(LanguageLevelModuleExtension::class.java)
      langLevelExt.languageLevel = LanguageLevel.JDK_1_8
      rootManager.commit()
    }

    // Stub a Java 9 API.
    fixture.addFileToProject(
      "java/util/Objects.java",
      // language=JAVA
      """
      package java.util;
      public final class Objects {
        /** @since 9 */
        public static <T> T requireNonNullElse(T obj, T defaultObj) {
            return (obj != null) ? obj : requireNonNull(defaultObj, "defaultObj");
        }
      }
      """
        .trimIndent(),
    )

    // Add a Java 9 API usage.
    fixture.configureByText(
      "Test.java",
      // language=JAVA
      """
      import java.util.Objects;
      public class Test {
        public String testJava9APIUsage(String s) {
          // This API is allowed in Android modules via desugaring.
          return Objects.requireNonNullElse(s, "default");
        }
      }
      """
        .trimIndent(),
    )

    // Verify JavaApiUsageInspection does not report any errors.
    fixture.enableInspections(JavaApiUsageInspection::class.java)
    fixture.checkHighlighting()

    // Remove the Android facet.
    runWriteActionAndWait {
      val facetModel = FacetManager.getInstance(module).createModifiableModel()
      facetModel.removeFacet(AndroidFacet.getInstance(module))
      facetModel.commit()
    }

    // Verify JavaApiUsageInspection works as expected on non-Android modules.
    // Note: "Since15" is the tool ID for JavaApiUsageInspection.
    val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
    assertThat(errors.map(HighlightInfo::getInspectionToolId)).isEqualTo(listOf("Since15"))
  }
}
