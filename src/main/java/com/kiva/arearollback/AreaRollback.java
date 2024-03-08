package com.kiva.arearollback;

import com.fox2code.foxloader.loader.Mod;

public class AreaRollback extends Mod {
    public static final String loggingPrefix = "[AreaRollback] ";
    public static final String version = "0.0.4";

    @Override
    public void onPreInit() {
        System.out.println("AreaRollback initializing...");
    }
}
