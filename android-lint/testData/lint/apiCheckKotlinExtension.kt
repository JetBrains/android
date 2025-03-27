package p1.p2

fun test(list: MutableList<String>) {
    list.<warning descr="This Kotlin extension function will be hidden by `java.util.SequencedCollection` starting in API 35: `removeFirst`. When this source code is recompiled against API level 35, it will crash on older levels. You can avoid this by using `removeAt(0)` instead.">removeFirst()</warning>
    list.<warning descr="This Kotlin extension function will be hidden by `java.util.SequencedCollection` starting in API 35: `removeLast`. When this source code is recompiled against API level 35, it will crash on older levels. You can avoid this by using `removeAt(list.lastIndex)` instead.">remove<caret>Last()</warning>
}
