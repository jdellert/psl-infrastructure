package de.tuebingen.sfs.psl.util.log;

import java.io.PrintStream;
import java.util.function.Consumer;

public class InferenceLogger {

    private Consumer<String> guiStream;
    private PrintStream logStream;

    public InferenceLogger() {
        this(null);
    }

    public InferenceLogger(Consumer<String> guiStream) {
        this(guiStream, System.err);
    }

    public InferenceLogger(Consumer<String> guiStream, PrintStream logStream) {
        this.guiStream = guiStream;
        this.logStream = logStream;
    }

    public Consumer<String> getGuiStream() {
        return guiStream;
    }

    public void setGuiStream(Consumer<String> guiStream) {
        this.guiStream = guiStream;
    }

    public PrintStream getLogStream() {
        return logStream;
    }

    public void setLogStream(PrintStream logStream) {
        this.logStream = logStream;
    }

    public void display(String msg) {
        if (guiStream != null)
            guiStream.accept(msg);
    }

    public void displayln(String msg) {
        display(msg + "\n");
    }

    public void log(String msg) {
        logStream.print(msg);
    }

    public void logln(String msg) {
        logStream.println(msg);
    }

    public void displayAndLog(String msg) {
        display(msg);
        log(msg);
    }

    public void displayAndLogLn(String msg) {
        displayln(msg);
        logln(msg);
    }
}
