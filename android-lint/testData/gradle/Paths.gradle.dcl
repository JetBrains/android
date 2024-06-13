declarativeDependencies {
    compile(<warning descr="Do not use Windows file separators in .gradle files; use / instead">files("my\\libs\\http.jar")</warning>)
    compile(<warning descr="Avoid using absolute paths in .gradle files">files("/libs/android-support-v4.jar")</warning>)
}
