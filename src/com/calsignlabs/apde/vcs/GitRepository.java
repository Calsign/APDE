package com.calsignlabs.apde.vcs;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

import com.calsignlabs.apde.APDE;

public class GitRepository {
	public static final String MASTER_BRANCH = "master";
	public static final String REMOTE_NAME = "origin";
	
	private File rootDir;
	private Git git;
	
	public GitRepository(File dir) {
		rootDir = dir;
		
		try {
			git = new Git(new FileRepository(getGitDir()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean exists() {
		return getGitDir().exists();
	}
	
	public void initRepo() {
		if (exists()) {
			return;
		}
		
		try {
			Git.init().setDirectory(getGitDir()).setBare(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}
	
	public static void cloneRepo(String cloneFrom, File cloneTo) {
		Git result = null;
		
		try {
			result = Git.cloneRepository().setURI(cloneFrom).setDirectory(cloneTo)
					.setBranch(MASTER_BRANCH).setBare(false).setRemote(REMOTE_NAME)
					.setNoCheckout(false).setCloneAllBranches(false).setCloneSubmodules(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
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
			git.pull().setRemote(REMOTE_NAME).setRemoteBranchName(MASTER_BRANCH)
					.setRebase(false).call();
		} catch (InvalidRemoteException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (GitAPIException e) {
			e.printStackTrace();
		}
	}
	
	public void close() {
		git.close();
	}
	
	public File getRootDir() {
		return rootDir;
	}
	
	public File getGitDir() {
		return new File(rootDir, ".git");
	}
}