@SuppressWarnings("WeakerAccess")
public class B {
    public static void b(int id) {
        C.c(id);
    }

    public static void fromB(int id) {
        A.fromA(id);
    }

}
