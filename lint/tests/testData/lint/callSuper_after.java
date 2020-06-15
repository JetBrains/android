package p1.p2;

import android.support.annotation.CallSuper;

import java.util.List;
import java.util.Map;

@SuppressWarnings("UnusedDeclaration")
public class CallSuperTest {
  private static class Child extends Parent {
    @Override
    protected void test1(String first, int second) {
        super.test1(first, second);
    }
  }

  private static class Parent extends ParentParent {
    @Override
    protected void test1(String first, int second) {
      super.test1(first, second);
    }
  }

  private static class ParentParent extends ParentParentParent {
  }

  private static class ParentParentParent {
    @android.support.annotation.CallSuper
    protected void test1(String first, int second) {

    }

  }
}
