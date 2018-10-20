package com.calsignlabs.apde.build.dag;

import java.util.Collections;
import java.util.List;

public interface Getter<T> {
	T get(BuildContext context);
	
	default List<BuildTask> getDependencies() {
		return Collections.emptyList();
	}
	
	default boolean hasDependencies() {
		return !getDependencies().isEmpty();
	}
	
	static <T> Getter<T> wrap(T t) {
		return context -> t;
	}
	
	static <T> Getter<T> getNull() {
		return context -> null;
	}
}