{
	System.setOut(new java.io.PrintStream(new APDEInternalConsoleStream('o')));
	System.setErr(new java.io.PrintStream(new APDEInternalConsoleStream('e')));

	Thread.setDefaultUncaughtExceptionHandler(new APDEInternalExceptionHandler());
}

private class APDEInternalExceptionHandler implements Thread.UncaughtExceptionHandler {
	private Thread.UncaughtExceptionHandler defaultHandler;

	private APDEInternalExceptionHandler() {
		defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
	}

	public void uncaughtException(Thread t, Throwable e) {
		e.printStackTrace();
		APDEInternalBroadcastMessage(e.getMessage(), 'x', e.getClass().getName());
		defaultHandler.uncaughtException(t, e);
	}
}

private class APDEInternalConsoleStream extends OutputStream {
	final byte single[] = new byte[1];
	final char severity;

	public APDEInternalConsoleStream(char severity) {
		this.severity = severity;
	}

	@Override
	public void close() {}
	@Override
	public void flush() {}

	@Override
	public void write(byte b[]) {
		write(b, 0, b.length);
	}

	@Override
	public void write(byte b[], int offset, int length) {
		APDEInternalBroadcastMessage(new String(b, offset, length), severity, "");
	}

	@Override
	public void write(int b) {
		single[0] = (byte) b;
		write(single, 0, 1);
	}
}

private void APDEInternalBroadcastMessage(final String message, final char severity, final String exception) {
	runOnUiThread(new Runnable() {
		public void run() {
			android.content.Intent intent = new android.content.Intent();
			intent.setAction("com.calsignlabs.apde.LogBroadcast");
			intent.putExtra("com.calsignlabs.apde.LogSeverity", severity);
			intent.putExtra("com.calsignlabs.apde.LogMessage", message);
			intent.putExtra("com.calsignlabs.apde.LogException", exception);
			sendBroadcast(intent);
		}
	});
}
