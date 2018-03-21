package test.pkg;

public class StopShip {
    public void hack() { // <error descr="`STOPSHIP` comment found; points to code which must be fixed prior to release">STOP<caret>SHIP</error>
    }
}