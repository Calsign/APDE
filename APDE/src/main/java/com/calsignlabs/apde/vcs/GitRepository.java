package com.calsignlabs.apde.vcs;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;

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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

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
	
	public ArrayList<CharSequence> getRecentCommitMessages(int num) {
		ArrayList<RevCommit> commits = getRecentCommits(num);
		ArrayList<CharSequence> commitMessages = new ArrayList<CharSequence>(commits.size());
		
		for (RevCommit commit : commits) {
			commitMessages.add(commit.getFullMessage());
		}
		
		return commitMessages;
	}
	
	public ArrayList<CharSequence> getRecentCommitMessages(ArrayList<RevCommit> commits) {
		ArrayList<CharSequence> commitMessages = new ArrayList<CharSequence>(commits.size());
		
		for (RevCommit commit : commits) {
			commitMessages.add(commit.getFullMessage());
		}

		return commitMessages;
	}
	
	public DiffFormatter getDiffFormatter(OutputStream out) {
		DiffFormatter formatter = new DiffFormatter(out);
		formatter.setRepository(git.getRepository());
		formatter.setDiffComparator(RawTextComparator.DEFAULT);
		formatter.setDetectRenames(true);
		
		return formatter;
	}

    /**
     * @return a lit of possible actions for this repository
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

					showLayoutAlert(context.getEditor(), R.string.git_pull, layout, R.string.git_pull_button, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int button) {
							final String remote = ((EditText) layout.findViewById(R.id.git_pull_remote)).getText().toString();

							final String username = ((EditText) layout.findViewById(R.id.git_credentials_username)).getText().toString();
							final char[] password = ((EditText) layout.findViewById(R.id.git_credentials_password)).getText().toString().toCharArray();

							new Thread(new Runnable() {
								public void run() {
									if (username.length() > 0 || password.length > 0) {
										repo.pullRepo(remote, MASTER_BRANCH, new GitUser(username, password, "", ""));
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
								}
							}).start();
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
					
					showLayoutAlert(context.getEditor(), R.string.git_push, layout, R.string.git_push_button, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int button) {
							final String remote = ((EditText) layout.findViewById(R.id.git_push_remote)).getText().toString();
							
							final String username = ((EditText) layout.findViewById(R.id.git_credentials_username)).getText().toString();
							final char[] password = ((EditText) layout.findViewById(R.id.git_credentials_password)).getText().toString().toCharArray();
							
							new Thread(new Runnable() {
								public void run() {
									repo.pushRepo(remote, new GitUser(username, password, "", ""));
								}
							}).start();
						}
					});
				}
			});
			
			actions.add(new GitAction(context, this, R.string.git_snapshot) {
				@Override
				public void run() {
					final LinearLayout layout = (LinearLayout) inflateLayout(context, R.layout.git_commit);
					
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

									new Thread(new Runnable() {
										public void run() {
											repo.addAndCommit(message, new GitUser("", new char[0], name, email));
										}
									}).start();
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
					try {
						APDE.deleteFile(repo.getGitDir());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
        } else {
			if (context.getSketchLocationType() == APDE.SketchLocation.SKETCHBOOK || context.getSketchLocationType() == APDE.SketchLocation.EXTERNAL) {
				actions.add(new GitAction(context, this, R.string.git_init) {
					@Override
					public void run() {
						repo.initRepo();
					}
				});
			}
		}
		
		actions.add(new GitAction(context, this, R.string.git_clone) {
			@Override
			public void run() {
				final LinearLayout layout = (LinearLayout) inflateLayout(context, R.layout.git_clone);

				showLayoutAlert(context.getEditor(), R.string.git_clone, layout, R.string.git_clone_button, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int button) {
						final String remote = ((EditText) layout.findViewById(R.id.git_clone_remote)).getText().toString();
						String localName = ((EditText) layout.findViewById(R.id.git_clone_local_name)).getText().toString();

						final File destDir = new File(context.getSketchbookFolder(), localName);

						final String username = ((EditText) layout.findViewById(R.id.git_credentials_username)).getText().toString();
						final char[] password = ((EditText) layout.findViewById(R.id.git_credentials_password)).getText().toString().toCharArray();

						new Thread(new Runnable() {
							public void run() {
								if (username.length() > 0 || password.length > 0) {
									cloneRepo(remote, destDir, MASTER_BRANCH, new GitUser(username, password, "", ""));
								} else {
									cloneRepo(remote, destDir, MASTER_BRANCH);
								}
								
								context.getEditor().runOnUiThread(new Runnable() {
									@Override
									public void run() {
										context.getEditor().forceDrawerReload();
									}
								});
							}
						}).start();
					}
				});
			}
		});

        return actions;
    }

	private View inflateLayout(Context context, int layoutId) {
		View layout;

		if(android.os.Build.VERSION.SDK_INT >= 11) {
			layout = (View) View.inflate(new ContextThemeWrapper(context, android.R.style.Theme_Holo_Dialog), layoutId, null);
		} else {
			layout = (View) View.inflate(new ContextThemeWrapper(context, android.R.style.Theme_Dialog), layoutId, null);
		}

		return layout;
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