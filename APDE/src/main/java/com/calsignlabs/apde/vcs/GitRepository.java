package com.calsignlabs.apde.vcs;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AlertDialog;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.task.DeleteFileTask;
import com.calsignlabs.apde.task.Task;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

public class GitRepository {
	public static final String MASTER_BRANCH = "master";
	public static final String REMOTE_NAME = "origin";
	
	private File rootDir;
	private Git git;
	
	public GitRepository(File dir) {
		rootDir = dir;
		
		open();
	}
	
	public boolean exists() {
		return getGitDir().exists();
	}
	
	public void initRepo() {
		if (exists()) {
			return;
		}
		
		try {
			Git.init().setDirectory(getRootDir()).setBare(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Adds a rule to the .gitignore file. Creates this file if it does not exist.
	 * 
	 * @param rules
	 */
	public void addIgnoreRules(String[] rules) {
		File gitignoreFile = new File(rootDir, ".gitignore");
		
		FileOutputStream fileOut = null;
		BufferedOutputStream out = null;
		
		try {
			fileOut = new FileOutputStream(gitignoreFile, true);
			out = new BufferedOutputStream(fileOut);
			
			for (String rule : rules) {
				out.write((rule + "\n").getBytes());
			}
			
			out.flush();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
				
				if (fileOut != null) {
					fileOut.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void cloneRepo(String uri, File cloneTo) {
		Git result = null;
		
		try {
			result = Git.cloneRepository().setURI(uri).setDirectory(cloneTo)
					.setBranch(MASTER_BRANCH).setBare(false).setRemote(REMOTE_NAME)
					.setNoCheckout(false).setCloneAllBranches(false).setCloneSubmodules(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (result != null) {
				result.close();
			}
		}
	}
	
	public static void cloneRepo(String uri, File cloneTo, String branch) {
		Git result = null;
		
		try {
			result = Git.cloneRepository().setURI(uri).setDirectory(cloneTo)
					.setBranch(branch).setBare(false).setRemote(REMOTE_NAME)
					.setNoCheckout(false).setCloneAllBranches(false).setCloneSubmodules(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (result != null) {
				result.close();
			}
		}
	}
	
	public static void cloneRepo(String uri, File cloneTo, String branch, GitUser user) {
		Git result = null;
		
		try {
			result = Git.cloneRepository().setURI(uri).setDirectory(cloneTo)
					.setBranch(branch).setBare(false).setRemote(REMOTE_NAME)
					.setNoCheckout(false).setCloneAllBranches(false).setCloneSubmodules(false)
					.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.getUsername(), user.getPassword())).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (result != null) {
				result.close();
			}
		}
	}
	
	public boolean canPull() {
		//Determine whether or not the remote repository has new commits that need to be pulled
		//This just compares the local and remote HEADs - if they're different, then we can pull
		
		try {
			Collection<Ref> refs = Git.lsRemoteRepository().setHeads(true).setTags(true).setRemote(APDE.EXAMPLES_REPO).call();
			
			ObjectId remoteHead = refs.iterator().next().getObjectId();
			ObjectId localHead = git.getRepository().resolve("HEAD");
			
			if (remoteHead == null) {
				return false;
			}
			
			if (localHead == null) {
				return true;
			}
			
			return !remoteHead.toString().equals(localHead.toString());
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (MissingObjectException e) {
			e.printStackTrace();
		} catch (IncorrectObjectTypeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	public void pullRepo() {
		try {
			git.pull().setRemote(REMOTE_NAME).setRemoteBranchName(MASTER_BRANCH).setRebase(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	public void pullRepo(String uri, String branch) {
		try {
			setRemote(uri);
			git.pull().setRemote(REMOTE_NAME).setRemoteBranchName(branch).setRebase(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}

	public void pullRepo(String uri, String branch, GitUser user) {
		try {
			setRemote(uri);
			git.pull().setRemote(REMOTE_NAME).setRemoteBranchName(branch).setRebase(false)
					.setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.getUsername(), user.getPassword())).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}
	
	public void pushRepo(String uri, GitUser user) {
		try {
			setRemote(uri);
			git.push().setRemote(REMOTE_NAME).setCredentialsProvider(new UsernamePasswordCredentialsProvider(user.getUsername(), user.getPassword())).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}
	
	public void addAndCommit(String message, GitUser user) {
		try {
			git.add().addFilepattern(".").call();
			git.commit().setMessage(message).setAuthor(user.getName(), user.getEmail()).setCommitter(user.getName(), user.getEmail()).setAll(true).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Get a list of recent commits
	 * 
	 * @param num the number of commits to get, use -1 to get all commits
	 * @return the list of commits
	 */
	public ArrayList<RevCommit> getRecentCommits(int num) {
		ArrayList<RevCommit> commits = new ArrayList<RevCommit>();
		
		try {
			Iterator<RevCommit> it;
			
			//Pass in -1 to get all commits
			if (num == -1) {
				it = git.log().add(git.getRepository().resolve(Constants.HEAD)).call().iterator();
			} else {
				it = git.log().add(git.getRepository().resolve(Constants.HEAD)).setMaxCount(num).call().iterator();
			}
			
			while (it.hasNext()) {
				commits.add(it.next());
			}
			
			return commits;
		} catch (AmbiguousObjectException e) {
			e.printStackTrace();
		} catch (IncorrectObjectTypeException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			//This is thrown when there are no commits / there is not head
			//Is there a better way to deal with this?
		}
		
		return commits;
	}
	
	public ArrayList<CharSequence> getRecentCommitMessages(int num, int truncateAt) {
		ArrayList<RevCommit> commits = getRecentCommits(num);
		ArrayList<CharSequence> commitMessages = new ArrayList<CharSequence>(commits.size());
		
		for (RevCommit commit : commits) {
			//Ellipsize if necessary
			commitMessages.add(ellipsizeCommitMessage(commit, truncateAt));
		}
		
		return commitMessages;
	}
	
	public ArrayList<CharSequence> getRecentCommitMessages(ArrayList<RevCommit> commits, int truncateAt) {
		ArrayList<CharSequence> commitMessages = new ArrayList<CharSequence>(commits.size());
		
		for (RevCommit commit : commits) {
			//Ellipsize if necessary
			commitMessages.add(ellipsizeCommitMessage(commit, truncateAt));
		}

		return commitMessages;
	}
	
	public static String ellipsizeCommitMessage(RevCommit commit, int truncateAt) {
		String shortMessage = commit.getShortMessage();
		
		return truncateAt > 0 ?
				(shortMessage.length() > truncateAt
						? shortMessage.substring(0, Math.min(truncateAt, shortMessage.length())) + "…"
						: shortMessage)
				: commit.getFullMessage();
	}
	
	public DiffFormatter getDiffFormatter(OutputStream out) {
		DiffFormatter formatter = new DiffFormatter(out);
		formatter.setRepository(git.getRepository());
		formatter.setDiffComparator(RawTextComparator.DEFAULT);
		formatter.setDetectRenames(true);
		
		return formatter;
	}
	
	/**
	 * @param context the application context
	 * @return a list of possible actions for this repository
	 */
    public ArrayList<GitAction> getActions(final APDE context) {
        ArrayList<GitAction> actions = new ArrayList<GitAction>();
		
        if (exists()) {
			actions.add(new GitAction(context, this, R.string.git_pull) {
				@Override
				public void run() {
					final LinearLayout layout = (LinearLayout) inflateLayout(context, R.layout.git_pull);
					
					String origin = getRemote();
					
					if (origin != null) {
						//If the repository already has a remote, then we can fill it in
						((EditText) layout.findViewById(R.id.git_pull_remote)).setText(origin);
					}
					
					GitUser savedUser = new GitUser(context);
					((EditText) layout.findViewById(R.id.git_credentials_username)).setText(savedUser.getUsername());
					
					showLayoutAlert(context.getEditor(), R.string.git_pull, layout, R.string.git_pull_button, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int button) {
							final String remote = ((EditText) layout.findViewById(R.id.git_pull_remote)).getText().toString();

							final String username = ((EditText) layout.findViewById(R.id.git_credentials_username)).getText().toString();
							final char[] password = ((EditText) layout.findViewById(R.id.git_credentials_password)).getText().toString().toCharArray();
							
							context.getTaskManager().launchTask("gitPullTask", true, context.getEditor(), true, new Task() {
								@Override
								public void run() {
									postStatus(context.getResources().getString(R.string.git_task_pull_begin));
									
									if (username.length() > 0 || password.length > 0) {
										GitUser user = new GitUser(username, password, "", "");
										
										repo.pullRepo(remote, MASTER_BRANCH, user);
										
										user.saveUser(context);
									} else {
										repo.pullRepo(remote, MASTER_BRANCH);
									}
									
									context.getEditor().runOnUiThread(new Runnable() {
										@Override
										public void run() {
											//Update for any changes
											context.getEditor().reloadSketch();
										}
									});
									
									postStatus(context.getResources().getString(R.string.git_task_pull_finish));
								}
								
								@Override
								public CharSequence getTitle() {
									return context.getResources().getString(R.string.git_pull);
								}
							});
						}
					});
				}
			});
			
			actions.add(new GitAction(context, this, R.string.git_push) {
				@Override
				public void run() {
					final LinearLayout layout = (LinearLayout) inflateLayout(context, R.layout.git_push);
					
					String origin = getRemote();
					
					if (origin != null) {
						//If the repository already has a remote, then we can fill it in
						((EditText) layout.findViewById(R.id.git_push_remote)).setText(origin);
					}

					GitUser savedUser = new GitUser(context);
					((EditText) layout.findViewById(R.id.git_credentials_username)).setText(savedUser.getUsername());
					
					showLayoutAlert(context.getEditor(), R.string.git_push, layout, R.string.git_push_button, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int button) {
							final String remote = ((EditText) layout.findViewById(R.id.git_push_remote)).getText().toString();
							
							final String username = ((EditText) layout.findViewById(R.id.git_credentials_username)).getText().toString();
							final char[] password = ((EditText) layout.findViewById(R.id.git_credentials_password)).getText().toString().toCharArray();
							
							context.getTaskManager().launchTask("gitPushTask", true, context.getEditor(), true, new Task() {
								@Override
								public void run() {
									postStatus(context.getResources().getString(R.string.git_task_push_begin));
									
									GitUser user = new GitUser(username, password, "", "");
									
									repo.pushRepo(remote, user);
									
									user.saveUser(context);
									
									postStatus(context.getResources().getString(R.string.git_task_push_finish));
								}
								
								@Override
								public CharSequence getTitle() {
									return context.getResources().getString(R.string.git_push);
								}
							});
						}
					});
				}
			});
			
			actions.add(new GitAction(context, this, R.string.git_snapshot) {
				@Override
				public void run() {
					final LinearLayout layout = (LinearLayout) inflateLayout(context, R.layout.git_commit);
					
					GitUser savedUser = new GitUser(context);
					((EditText) layout.findViewById(R.id.git_user_info_name)).setText(savedUser.getName());
					((EditText) layout.findViewById(R.id.git_user_info_email)).setText(savedUser.getEmail());
					
					showLayoutAlert(context.getEditor(), R.string.git_snapshot, layout, R.string.git_snapshot_button, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int button) {
							final String message = ((EditText) layout.findViewById(R.id.git_commit_message)).getText().toString();
							
							final String name = ((EditText) layout.findViewById(R.id.git_user_info_name)).getText().toString();
							final String email = ((EditText) layout.findViewById(R.id.git_user_info_email)).getText().toString();
							
							context.getEditor().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									//Make sure we use the most updated version of the files
									context.getEditor().autoSave();
									
									context.getTaskManager().launchTask("gitAddCommitTask", false, null, true, new Task() {
										@Override
										public void run() {
											postStatus(context.getResources().getString(R.string.git_task_snapshot_begin));
											
											GitUser user = new GitUser("", new char[0], name, email);
											
											repo.addAndCommit(message, user);
											
											user.saveUser(context);
											
											postStatus(context.getResources().getString(R.string.git_task_snapshot_finish));
										}
										
										@Override
										public CharSequence getTitle() {
											return context.getResources().getString(R.string.git_snapshot);
										}
									});
								}
							});
						}
					});
				}
			});
			
			actions.add(new GitAction(context, this, R.string.git_history) {
				@Override
				public void run() {
					Intent gitHistoryIntent = new Intent(context, GitHistoryActivity.class);
					context.getEditor().startActivity(gitHistoryIntent);
				}
			});
			
			actions.add(new GitAction(context, this, R.string.git_delete) {
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
					
					builder.setTitle(R.string.git_delete_confirmation_dialog_title);
					builder.setMessage(String.format(Locale.US, context.getResources().getString(R.string.git_delete_confirmation_dialog_message), context.getSketchName()));
					
					builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							launchGitDeleteTask(context, repo);
						}
					});
					
					builder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {}
					});
					
					builder.create().show();
				}
			});
        } else {
			if (context.getSketchLocationType() == APDE.SketchLocation.SKETCHBOOK || context.getSketchLocationType() == APDE.SketchLocation.EXTERNAL) {
				actions.add(new GitAction(context, this, R.string.git_init) {
					@Override
					public void run() {
						context.getTaskManager().launchTask("gitInitTask", false, null, true, new Task() {
							@Override
							public void run() {
								postStatus(context.getResources().getString(R.string.git_task_init_begin));
								
								repo.initRepo();
								
								postStatus(context.getResources().getString(R.string.git_task_init_gitignore_begin));
								
								addIgnoreRules(new String[] {"bin/**"});
								
								postStatus(context.getResources().getString(R.string.git_task_init_finish));
							}
							
							@Override
							public CharSequence getTitle() {
								return context.getResources().getString(R.string.git_init);
							}
							
							@Override
							public void cancel() {
								//Undo progress by deleting the repository
								launchGitDeleteTask(context, repo);
							}
						});
					}
				});
			}
		}
		
		actions.add(new GitAction(context, this, R.string.git_clone) {
			@Override
			public void run() {
				final LinearLayout layout = (LinearLayout) inflateLayout(context, R.layout.git_clone);
				
				GitUser savedUser = new GitUser(context);
				((EditText) layout.findViewById(R.id.git_credentials_username)).setText(savedUser.getUsername());
				
				showLayoutAlert(context.getEditor(), R.string.git_clone, layout, R.string.git_clone_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int button) {
						final String remote = ((EditText) layout.findViewById(R.id.git_clone_remote)).getText().toString();
						final String localName = ((EditText) layout.findViewById(R.id.git_clone_local_name)).getText().toString();
						
						final File destDir = new File(context.getSketchbookFolder(), localName);
						
						final String username = ((EditText) layout.findViewById(R.id.git_credentials_username)).getText().toString();
						final char[] password = ((EditText) layout.findViewById(R.id.git_credentials_password)).getText().toString().toCharArray();
						
						context.getTaskManager().launchTask("gitCloneTask", true, context.getEditor(), true, new Task() {
							@Override
							public void run() {
								postStatus(context.getResources().getString(R.string.git_task_clone_begin));
								
								if (username.length() > 0 || password.length > 0) {
									GitUser user = new GitUser(username, password, "", "");
									
									cloneRepo(remote, destDir, MASTER_BRANCH, user);
									
									user.saveUser(context);
								} else {
									cloneRepo(remote, destDir, MASTER_BRANCH);
								}
								
								postStatus(context.getResources().getString(R.string.git_task_clone_finish));
								
								context.getEditor().runOnUiThread(new Runnable() {
									@Override
									public void run() {
										context.getEditor().forceDrawerReload();
									}
								});
							}
							
							@Override
							public CharSequence getTitle() {
								return context.getResources().getString(R.string.git_clone);
							}
							
							@Override
							public void cancel() {
								//Undo progress by deleting the folder
								context.getTaskManager().launchTask("gitCloneUndoTask", false, null, true,
										new DeleteFileTask(destDir, context.getResources().getString(R.string.git_delete), null,
												context.getResources().getString(R.string.git_task_delete_begin),
												context.getResources().getString(R.string.git_task_delete_finish),
												context.getResources().getString(R.string.git_task_delete_fail)));
							}
						});
					}
				});
			}
		});
		
        return actions;
    }
	
	public void launchGitDeleteTask(APDE context, GitRepository repo) {
		context.getTaskManager().launchTask("gitDeleteTask", false, null, true,
				new DeleteFileTask(repo.getGitDir(), context.getResources().getString(R.string.git_delete), null,
						context.getResources().getString(R.string.git_task_delete_begin),
						context.getResources().getString(R.string.git_task_delete_finish),
						context.getResources().getString(R.string.git_task_delete_fail)));
	}
	
	private View inflateLayout(Context context, int layoutId) {
		return View.inflate(new ContextThemeWrapper(context, R.style.Theme_AppCompat_Dialog), layoutId, null);
	}
	
	private void showLayoutAlert(Activity context, int titleId, View layout, int positiveButtonTitleId, DialogInterface.OnClickListener positiveListener) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(titleId);
		
		builder.setView(layout);
		
		builder.setPositiveButton(positiveButtonTitleId, positiveListener);
		builder.setNegativeButton(R.string.cancel, null);
		
		builder.create().show();
	}
	
    /**
     * @return a list of names of possible actions for this repository
     */
    public String[] getActionNames(ArrayList<GitAction> actions) {
        String[] actionNames = new String[actions.size()];
		
        for (int i = 0; i < actions.size(); i ++) {
            actionNames[i] = actions.get(i).getName();
        }
		
        return actionNames;
    }
	
    /**
     * @return a list of names of possible actions for this repository
     */
    public String[] getActionNames(APDE context) {
        ArrayList<GitAction> actions = getActions(context);
        String[] actionNames = new String[actions.size()];
		
        for (int i = 0; i < actions.size(); i ++) {
            actionNames[i] = actions.get(i).getName();
        }
		
        return actionNames;
    }
	
    public abstract class GitAction implements Runnable {
		protected APDE context;
        protected GitRepository repo;
		protected String name;
		
        public GitAction(APDE context, GitRepository repo, String name) {
			this.context = context;
            this.repo = repo;
			this.name = name;
        }
		
		public GitAction(APDE context, GitRepository repo, int nameId) {
			this.context = context;
			this.repo = repo;
			name = context.getResources().getString(nameId);
		}
		
        public String getName() {
			return name;
		}
    }
	
	public void close() {
		git.close();
	}
	
    public void open() {
        try {
            git = new Git(new FileRepository(getGitDir()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	public File getRootDir() {
		return rootDir;
	}
	
	public File getGitDir() {
		return new File(rootDir, ".git");
	}
	
	public Git getGit() {
		return git;
	}
	
	public StoredConfig getConfig() {
		return git.getRepository().getConfig();
	}
	
	public void setRemote(String remote) {
		String currentRemote = getRemote();
		
		if (currentRemote != null && currentRemote.equals(remote)) {
			return;
		}
		
		try {
			StoredConfig config = getConfig();
			config.setString("remote", REMOTE_NAME, "url", remote);
			config.save();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getRemote() {
		return getConfig().getString("remote", REMOTE_NAME, "url");
	}
}