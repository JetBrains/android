package p1.p2

fun test(list: MutableList<String>) {
    list.removeFirst()
    list.removeAt(list.lastIndex)
}
