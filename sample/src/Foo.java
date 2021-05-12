public class Foo {
    public void removed1() {}

    public void removed2() {}

    public void foo() {
        removed1();
        removed2();
        Bar b = new Bar();
        b.bar();
    }
}
