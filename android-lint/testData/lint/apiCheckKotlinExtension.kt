package p1.p2

fun test(list: MutableList<String>) {
    <warning descr="This Kotlin extension function will be hidden by `java.util.SequencedCollection` starting in API 35">list.removeFirst()</warning>
    <warning descr="This Kotlin extension function will be hidden by `java.util.SequencedCollection` starting in API 35">list.remove<caret>Last()</warning>
}
