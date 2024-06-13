declarativeDependencies {
    compile(files("my/libs/http1.jar"))
    //noinspection GradleDependency
    compile(<warning descr="Do not use Windows file separators in .gradle files; use / instead">files("my\\li<caret>bs\\http2.jar")</warning>)
}
