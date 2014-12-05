System.setOut(new java.io.PrintStream(new APDEInternalLogBroadcasterUtil.APDEInternalConsoleStream('o', this)));
System.setErr(new java.io.PrintStream(new APDEInternalLogBroadcasterUtil.APDEInternalConsoleStream('e', this)));

Thread.setDefaultUncaughtExceptionHandler(new APDEInternalLogBroadcasterUtil.APDEInternalExceptionHandler(this));