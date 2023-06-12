package library_lambda;

import java.util.function.Consumer;

class UsesLambda {
  private void takesConsumer(Consumer<Integer> consumer) {
    consumer.accept(42);
  }

  public void useLambda() {
    takesConsumer(x -> {
          System.out.println(x);
        });
  }
}
