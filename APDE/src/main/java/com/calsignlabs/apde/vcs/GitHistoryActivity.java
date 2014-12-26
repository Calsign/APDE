package com.calsignlabs.apde.vcs;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.SettingsActivity;
import com.calsignlabs.apde.SettingsActivityHC;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GitHistoryActivity extends ActionBarActivity {
	private GitRepository repo;
	private ArrayList<RevCommit> commits;
	private ArrayList<CharSequence> commitMessages;
	
	private CommitListFragment commitListFragment;
	private CommitDiffFragment commitDiffFragment;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_git_history);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
		
		//Get a list of commits
		
		repo = new GitRepository(((APDE) getApplicationContext()).getSketchLocation());
		//TODO Implement infinite scrolling with dynamic loading for sketches with lots of commits
		commits = repo.getRecentCommits(-1);
		commitMessages = repo.getRecentCommitMessages(commits);
		
		repo.close();
		
		if (savedInstanceState != null) {
			commitListFragment = (CommitListFragment) getSupportFragmentManager().getFragment(savedInstanceState, "commitList");
			commitDiffFragment = (CommitDiffFragment) getSupportFragmentManager().getFragment(savedInstanceState, "commitDiff");
		}
		
		if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE) {
			if (commitListFragment == null) {
				commitListFragment = new CommitListFragment();
				loadFragment(commitListFragment, R.id.git_history_commit_list_frame, false);
			}
			
			if (commitDiffFragment == null) {
				commitDiffFragment = new CommitDiffFragment();
				loadFragment(commitDiffFragment, R.id.git_history_commit_diff_frame, false);
			}
		} else {
			if (commitListFragment == null) {
				commitListFragment = new CommitListFragment();
				loadFragment(commitListFragment, R.id.git_history_frame, false);
			}
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		//We are currently retaining each fragment's instance across configuration changes...
		//...is this what we really want?
		//
		//The commit list works without it, but the commit diff needs it
		
		commitListFragment.setRetainInstance(true);
		getSupportFragmentManager().putFragment(outState, "commitList", commitListFragment);
		if (commitDiffFragment != null && commitDiffFragment.isAdded()) {
			commitDiffFragment.setRetainInstance(true);
			getSupportFragmentManager().putFragment(outState, "commitDiff", commitDiffFragment);
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();
	}
	
	protected void loadFragment(Fragment fragment, int id, boolean transition) {
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.replace(id, fragment);
		if (transition) {
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
			transaction.addToBackStack(null);
		}
		transaction.commit();
	}
	
	protected ArrayList<CharSequence> getCommitMessages() {
		return commitMessages;
	}
	
	private Thread loadThread;
	
	protected void selectCommit(final int num) {
		if (!((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE)) {
			commitDiffFragment = new CommitDiffFragment();
			loadFragment(commitDiffFragment, R.id.git_history_frame, true);
		} else {
			commitListFragment.selectItem(num);
		}
		
		if (loadThread != null) {
			loadThread.interrupt();
		}
		
		loadThread = new Thread(new Runnable() {
			public void run() {
				repo.open();

				RevCommit commit = commits.get(num);
				RevTree commitTree = commit.getTree();

				RevCommit parent = null;
				RevTree parentTree;

				if (commit.getParentCount() == 0) {
					//This is the first commit
					parentTree = null;
				} else {
					parent = commit.getParent(0);
					parentTree = parent.getTree();
				}

				ByteArrayOutputStream out = new ByteArrayOutputStream();
				DiffFormatter formatter = repo.getDiffFormatter(out);

				final ArrayList<CommitDiff> commitDiffs = new ArrayList<CommitDiff>();

				try {
					List<DiffEntry> diffEntries = formatter.scan(parentTree, commitTree);

					for (DiffEntry diffEntry : diffEntries) {
						String changeType = diffEntry.getChangeType().name().toUpperCase();
						//If we're deleting, at least show a path that's useful
						String path;
						if (changeType.equalsIgnoreCase("delete")) {
							path = diffEntry.getOldPath();
						} else if (changeType.equalsIgnoreCase("rename")) {
							path = diffEntry.getOldPath() + " → " + diffEntry.getNewPath();
						} else {
							path = diffEntry.getNewPath();
						}

						formatter.format(diffEntry);
						Spannable diffText = cleanDiffText(out.toString());

						commitDiffs.add(new CommitDiff(changeType, path, diffText));

						out.reset();
					}
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				repo.close();
				
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						try {
							getSupportFragmentManager().executePendingTransactions();
							commitDiffFragment.setProgressVisibility(false);
							commitDiffFragment.setCommitDiffs(commitDiffs);
						} catch (NullPointerException e) {
							//This happens when the user has already navigated away from this screen
							//It took too long to load or something
							//Either way, we can't have the app crashing for no reason this long after leaving the screen...
						}
					}
				});
			}
		});
		
		loadThread.start();
		
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				getSupportFragmentManager().executePendingTransactions();
				commitDiffFragment.setProgressVisibility(true);
			}
		});
	}
	
	private Spannable cleanDiffText(String diffText) {
		String[] lines = diffText.split("\\r?\\n");
		SpannableStringBuilder output = new SpannableStringBuilder();
		
		boolean pastHeader = false;
		for (String line : lines) {
			if (!pastHeader) {
				//Filter out unwanted header text
				
				if (line.startsWith("diff --git")) continue;
				if (line.startsWith("index")) continue;
				if (line.startsWith("+++")) continue;
				if (line.startsWith("---")) continue;
				
				if (line.startsWith("old mode")) continue;
				if (line.startsWith("new mode")) continue;
				if (line.startsWith("new file mode")) continue;
				if (line.startsWith("deleted file mode")) continue;
				if (line.startsWith("similarity index")) continue;
				if (line.startsWith("rename from")) continue;
				if (line.startsWith("rename to")) continue;
				if (line.startsWith("copy from")) continue;
				if (line.startsWith("copy to")) continue;
				if (line.startsWith("dissimilarity index")) continue;
			}
			
			pastHeader = true;
			
			int outputLength = output.length();
			int lineLength = line.length();
			
			//We can remove the "+" and "-" characters from the beginning of the line if we want to
			//Currently opting not to for two reasons:
			// - People aren't used to it
			// - Blank lines aren't highlighted properly
			
			if (line.startsWith("+")) {
				output.append(line);
				output.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.git_diff_plus)), outputLength, outputLength + lineLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (line.startsWith("-")) {
				output.append(line);
				output.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.git_diff_minus)), outputLength, outputLength + lineLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (line.startsWith("@@") && line.endsWith("@@")) {
				output.append(line);
				output.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.git_diff_hunk_header)), outputLength, outputLength + lineLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (line.equals("\\ No newline at end of file")) {
				output.append(line);
				output.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.git_diff_no_newline)), outputLength, outputLength + lineLength, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			} else if (line.startsWith(" ")) {
				//Context lines
				output.append(line);
			} else {
				output.append(line);
			}
			
			output.append('\n');
		}
		
		//Trim off the trailing newline
		return output.length() > 0 ? output.delete(output.length() - 1, output.length()) : output;
	}
	
	protected class CommitDiff {
		public String changeType;
		public String path;
		public Spannable diffText;
		
		public CommitDiff(String changeType, String path, Spannable diffText) {
			this.changeType = changeType;
			this.path = path;
			this.diffText = diffText;
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_git_history, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case android.R.id.home:
				if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
					getSupportFragmentManager().popBackStack();
				} else {
					finish();
				}
				return true;
			case R.id.action_settings:
				launchSettings();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	private void launchSettings() {
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB)
			startActivity(new Intent(this, SettingsActivity.class));
		else
			startActivity(new Intent(this, SettingsActivityHC.class));
	}
	
	public static class CommitListFragment extends Fragment {
		private boolean loaded = false;
		private int selectedItem = -1;
		
		private View rootView;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			//http://stackoverflow.com/a/23533575
			if (rootView == null) {
				rootView = inflater.inflate(R.layout.fragment_git_history_commit, container, false);
			} else {
				((ViewGroup) rootView.getParent()).removeView(rootView);
			}
			
			return rootView;
		}
		
		@Override
		public void onStart() {
			super.onStart();
			
			loadCommitList();
		}
		
		@Override
		public void onSaveInstanceState(Bundle outState) {
			super.onSaveInstanceState(outState);
			
			if (getView() != null) {
				final ListView commitList = (ListView) getView().findViewById(R.id.git_history_commit_list);

				if (commitList != null) {
					outState.putInt("listIndex", commitList.getFirstVisiblePosition());
					View v = commitList.getChildAt(0);
					outState.putInt("listTop", v == null ? 0 : v.getTop());
					
					outState.putInt("selectedItem", selectedItem);
				}
			}
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
			
			if (getView() != null && savedInstanceState != null) {
				final ListView commitList = (ListView) getView().findViewById(R.id.git_history_commit_list);

				if (commitList != null) {
					commitList.setSelectionFromTop(savedInstanceState.getInt("listIndex", 0), savedInstanceState.getInt("listTop", 0));
					
					selectItem(savedInstanceState.getInt("selectedItem", -1));
				}
			}
		}
		
		public void loadCommitList() {
			if (!loaded) {
				final ListView commitList = (ListView) getView().findViewById(R.id.git_history_commit_list);

				final ArrayList<CharSequence> commitMessages = ((GitHistoryActivity) getActivity()).getCommitMessages();

				commitList.setAdapter(new BaseAdapter() {
					@Override
					public int getCount() {
						return commitMessages.size();
					}

					@Override
					public Object getItem(int position) {
						return commitMessages.get(position);
					}

					@Override
					public long getItemId(int position) {
						return position;
					}

					@Override
					public View getView(int position, View convertView, ViewGroup parent) {
						//Let's see if we can convert the old view - otherwise inflate a new one
						if (convertView == null) {
							LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
							convertView = inflater.inflate(R.layout.git_history_commit_list_item, parent, false);
						}
						
						convertView.setBackgroundColor(getResources().getColor(selectedItem == position
								? R.color.holo_select
								: android.R.color.transparent));

						CharSequence message = commitMessages.get(position);

						((TextView) convertView.findViewById(R.id.git_history_commit_list_item_text)).setText(message);

						return convertView;
					}
				});

				//If there aren't any commits, let the user know
				if (commitList.getCount() <= 0) {
					getView().findViewById(R.id.git_history_empty).setVisibility(View.VISIBLE);
				} else {
					getView().findViewById(R.id.git_history_empty).setVisibility(View.GONE);
				}
				
				commitList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parentAdapter, View view, int position, long id) {
						((GitHistoryActivity) getActivity()).selectCommit(position);
					}
				});
				
				loaded = true;
			}
		}
		
		public void selectItem(int num) {
			final ListView commitList = (ListView) getView().findViewById(R.id.git_history_commit_list);
			
			selectedItem = num;
			int selection = num - commitList.getFirstVisiblePosition();
			
			//Keep the selected commit on screen
			if (num < commitList.getFirstVisiblePosition() + 2 || num > commitList.getLastVisiblePosition() - 2) {
				commitList.setSelection(num);
			}
			
			for (int i = 0; i < commitList.getCount(); i ++) {
				View child = commitList.getChildAt(i);
				
				if (child != null) {
					child.setBackgroundColor(selection == i
							? getResources().getColor(R.color.holo_select)
							: getResources().getColor(android.R.color.transparent));
				}
			}
		}
	}
	
	public static class CommitDiffFragment extends Fragment {
		private View rootView;
		
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			//http://stackoverflow.com/a/23533575
			if (rootView == null) {
				rootView = inflater.inflate(R.layout.fragment_git_history_diff, container, false);
			} else {
				((ViewGroup) rootView.getParent()).removeView(rootView);
			}
			
			return rootView;
		}
		
		@Override
		public void onActivityCreated(Bundle savedInstanceState) {
			super.onActivityCreated(savedInstanceState);
		}
		
		@Override
		public void onStart() {
			super.onStart();
		}
		
		public void setCommitDiffs(final ArrayList<CommitDiff> commitDiffs) {
			final ListView diffList = (ListView) getView().findViewById(R.id.git_history_diff_list);
			diffList.setAdapter(new BaseAdapter() {
				@Override
				public int getCount() {
					return commitDiffs.size();
				}
				
				@Override
				public Object getItem(int position) {
					return commitDiffs.get(position);
				}
				
				@Override
				public long getItemId(int position) {
					return position;
				}
				
				@Override
				public View getView(int position, View convertView, ViewGroup parent) {
					//Let's see if we can convert the old view - otherwise inflate a new one
					if(convertView == null) {
						LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						convertView = inflater.inflate(R.layout.git_history_diff_item, parent, false);
					}
					
					CommitDiff commitDiff = commitDiffs.get(position);
					
					((TextView) convertView.findViewById(R.id.git_history_diff_item_change_type)).setText(commitDiff.changeType);
					((TextView) convertView.findViewById(R.id.git_history_diff_item_path)).setText(commitDiff.path);
					((TextView) convertView.findViewById(R.id.git_history_diff_item_diff_text)).setText(commitDiff.diffText);
					
					return convertView;
				}
			});

			//If there aren't any changes, let the user know
			if(diffList.getCount() <= 0) {
				getView().findViewById(R.id.git_diff_empty).setVisibility(View.VISIBLE);
			} else {
				getView().findViewById(R.id.git_diff_empty).setVisibility(View.GONE);
			}
		}
		
		public void setProgressVisibility(boolean visible) {
			ProgressBar progress = (ProgressBar) getView().findViewById(R.id.git_diff_progress_bar);
			progress.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}
}