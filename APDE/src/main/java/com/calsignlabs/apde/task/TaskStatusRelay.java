package com.calsignlabs.apde.task;

import java.util.ArrayList;

public interface TaskStatusRelay {
	public void postStatus(CharSequence status);
	public ArrayList<CharSequence> getStatusHistory();
	public void setStatusHistory(ArrayList<CharSequence> statusHistory);
	public abstract void close();
}
