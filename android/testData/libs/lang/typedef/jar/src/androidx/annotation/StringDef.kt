package androidx.annotation

/**
 * Copied verbatim (modulo some comments) from
 * [AOSP Source](https://source.corp.google.com/aosp-androidx/annotation/annotation/src/commonMain/kotlin/androidx/annotation/StringDef.kt)
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class StringDef(
  /** Defines the allowed constants for this element  */
  vararg val value: String = [],
  /**
   * Whether any other values are allowed. Normally this is
   * not the case, but this allows you to specify a set of
   * expected constants, which helps code completion in the IDE
   * and documentation generation and so on, but without
   * flagging compilation warnings if other values are specified.
   */
  val open: Boolean = false
)
