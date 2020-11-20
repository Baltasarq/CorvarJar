// CorvarJar (c) 2020 Baltasar MIT License <jbgarcia@uvigo.es>


package com.devbaltasarq.corvarjar;


public class LibInfo {
    public static final String NAME = "CorvarJar";
    public static final String VERSION = "v0.4 20201120";
    public static final String AUTHOR = "MILE Group";
    public static final String EDITION = "NextGen";

    public static String asShortString()
    {
        return NAME + ' ' + VERSION;
    }

    public static String asString()
    {
        return NAME + ' ' + VERSION
                + " \"" + EDITION + "\" - " + AUTHOR;
    }
}
