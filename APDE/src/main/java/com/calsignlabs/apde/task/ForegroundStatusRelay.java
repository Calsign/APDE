package com.calsignlabs.apde.task;

import com.calsignlabs.apde.support.CustomProgressDialog;

import java.util.ArrayList;

public class ForegroundStatusRelay implements TaskStatusRelay {
	private TaskManager taskManager;
	private CustomProgressDialog progressDialog;

	private ArrayList<CharSequence> statusHistory;
	
	public ForegroundStatusRelay(TaskManager taskManager, CustomProgressDialog progressDialog) {
		this.taskManager = taskManager;
		this.progressDialog = progressDialog;

		statusHistory = new ArrayList<CharSequence>();
	}
	
	@Override
	public void postStatus(final CharSequence status) {
		taskManager.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progressDialog.setProgressText(status.toString());
			}
		});
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
		progressDialog.dismiss();
	}
}
