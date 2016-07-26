package com.calsignlabs.apde.tool;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.vcs.GitRepository;

import java.util.ArrayList;

/**
 * Provides an interface for high-level Git access
 */
public class GitManager implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.GitManager";

	private APDE context;

	@Override
	public void init(APDE context) {
		this.context = context;
	}

	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.git_manager);
	}

	@Override
	public void run() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());

		final GitRepository repo = new GitRepository(context.getSketchLocation());

		final ArrayList<GitRepository.GitAction> actions = repo.getActions(context);
		final String[] actionNames = repo.getActionNames(actions);

		repo.close();

		builder.setTitle(R.string.git_manager);
		builder.setItems(actionNames, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				actions.get(which).run();
			}
		});

		builder.create().show();
	}

	@Override
	public KeyBinding getKeyBinding() {
		return null;
	}

	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return !sketchLocation.isExample();
	}

	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}