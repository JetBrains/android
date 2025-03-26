import com.myapp.R

fun foo() {
    val a = "some"
    val b = "text"
    val c = 47
    val d = 'd'
    val bar = context.getString(R.string.hello, a, b, c, d)
}
