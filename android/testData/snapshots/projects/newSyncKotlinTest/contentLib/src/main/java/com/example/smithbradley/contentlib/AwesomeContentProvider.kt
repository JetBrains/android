package com.example.smithbradley.contentlib

@MyAwesomeAnnotation
class AwesomeContentProvider {
  @MyAwesomeAnnotation
  fun produceContent() : String {
    @MyAwesomeAnnotation
    return """
      I am some awesome content!
    """.trimIndent()
  }
}
