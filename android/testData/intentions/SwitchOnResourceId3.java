package p1.p2;

public class Test1 {
  public void f(int n) {
    switch (n) {
        case <error descr="Constant expression required"><error descr="Resource IDs cannot be used in a switch statement in Android library modules">R.drawable.icon</error></error>,
           1,
           <error descr="Constant expression required"><error descr="Resource IDs cannot be used in a switch statement in Android library modules">R.dra<caret>wable.icon2</error></error>,
           42:
        System.out.println("Icon");
        break;
    }
  }
}