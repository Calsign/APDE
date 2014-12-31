package com.calsignlabs.apde.task;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;

import java.io.File;
import java.io.IOException;

public class DeleteFileTask extends Task {
	private APDE context;
	
	private File toDelete;
	
	private CharSequence title;
	private CharSequence desc;
	
	private CharSequence deleteBegin;
	private CharSequence deleteFinish;
	private CharSequence deleteFail;
	
	public DeleteFileTask(File toDelete) {
		this.toDelete = toDelete;
	}
	
	public DeleteFileTask(File toDelete, CharSequence title, CharSequence desc, CharSequence beginMessage, CharSequence finishMessage, CharSequence failMessage) {
		this.toDelete = toDelete;
		
		this.title = title;
		this.desc = desc;
		
		deleteBegin = beginMessage;
		deleteFinish = finishMessage;
		deleteFail = failMessage;
	}
	
	@Override
	public void init(APDE context) {
		this.context = context;
		
		if (title == null) {
			title = context.getResources().getString(R.string.delete);
			desc = null;
			
			deleteBegin = context.getResources().getString(R.string.delete_task_begin);
			deleteFinish = context.getResources().getString(R.string.delete_task_finish);
			deleteFail = context.getResources().getString(R.string.delete_task_fail);
		}
	}
	
	@Override
	public CharSequence getTitle() {
		return title;
	}
	
	@Override
	public CharSequence getMessage() {
		return desc;
	}
	
	public void run() {
		postStatus(deleteBegin);
		
		try {
			APDE.deleteFile(toDelete);
			
			postStatus(deleteFinish);
		} catch (IOException e) {
			postStatus(deleteFail);
			e.printStackTrace();
		}
	}
}
