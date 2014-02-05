package com.welty.othello.engine;

import com.welty.othello.api.NBoardEngine;
import com.welty.othello.core.ProcessLogger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * An NBoard Engine reached via an external process
 */
public class ExternalNBoardEngine extends NBoardEngine {
    private final ProcessLogger processLogger;
    private volatile boolean shutdown = false;

    public ExternalNBoardEngine() throws IOException {
        this.processLogger = createProcessLogger();

        new Thread("NBoard Feeder") {
            @Override public void run() {
                while (!shutdown) {
                    try {
                        final String line = processLogger.readLine();

                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                fireMessageReceived(line);
                            }
                        });
                    } catch (IOException e) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                fireEngineTerminated();
                            }
                        });
                    }
                }
            }
        }.start();


    }

    private static ProcessLogger createProcessLogger() throws IOException {
        final String[] command = "./mEdax -nboard".split("\\s+");
        final File wd = new File("/Applications/edax/4.4/bin");
        final Process process = new ProcessBuilder(command).directory(wd).redirectErrorStream(true).start();
        return new ProcessLogger(process, true);
    }

    @Override public void sendCommand(String command) {
        processLogger.println(command);
    }
}
