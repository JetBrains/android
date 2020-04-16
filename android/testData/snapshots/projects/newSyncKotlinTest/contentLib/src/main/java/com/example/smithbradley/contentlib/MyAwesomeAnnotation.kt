package com.example.smithbradley.contentlib

@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.EXPRESSION, AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@MyAwesomeAnnotation // Madness
annotation class MyAwesomeAnnotation