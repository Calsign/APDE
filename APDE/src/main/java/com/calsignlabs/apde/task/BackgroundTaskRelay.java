package com.calsignlabs.apde.task;

import com.calsignlabs.apde.EditorActivity;

import java.util.ArrayList;

public class BackgroundTaskRelay implements TaskStatusRelay {
	private EditorActivity context;
	
	private ArrayList<CharSequence> statusHistory;
	
	public BackgroundTaskRelay(EditorActivity context) {
		this.context = context;
		
		statusHistory = new ArrayList<CharSequence>();
	}
	
	@Override
	public void postStatus(CharSequence status) {
		context.messageExt(status.toString());
		statusHistory.add(status);
	}
	
	@Override
	public ArrayList<CharSequence> getStatusHistory() {
		return statusHistory;
	}
	
	@Override
	public void setStatusHistory(ArrayList<CharSequence> statusHistory) {
		this.statusHistory = statusHistory;
		postStatus(statusHistory.get(statusHistory.size() - 1));
	}

	@Override
	public void close() {
		//Nothing to do here...
	}
}
