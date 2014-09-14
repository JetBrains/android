package test.pkg;

public class StopShip {
    public void hack() { // <warning descr="`STOPSHIP` comment found; points to code which must be fixed prior to release">STOP<caret>SHIP</warning>
    }
}