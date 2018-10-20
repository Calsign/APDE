package com.calsignlabs.apde.build.dag;

import java.util.List;

public class ArgLambdaBuildTask extends LambdaBuildTask {
	public ArgLambdaBuildTask(Action action, CharSequence title, BuildTask... deps) {
		super(action, title, deps);
	}
	
	protected static String[] argsArray(List<String> args) {
		return args.toArray(new String[args.size()]);
	}
	
	protected static <T> void addAll(List<T> list, T... things) {
		for (T t : things) {
			list.add(t);
		}
	}
}
