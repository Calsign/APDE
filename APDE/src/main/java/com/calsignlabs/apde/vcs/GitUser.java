package com.calsignlabs.apde.vcs;

import android.content.Context;
import android.content.SharedPreferences;

public class GitUser {
	private String username;
	private char[] password;
	private String name;
	private String email;
	
	public GitUser(Context context) {
		loadUser(context);
	}
	
	public GitUser(String username, char[] password, String name, String email) {
		this.username = username;
		this.password = password;
		this.name = name;
		this.email = email;
	}
	
	public void loadUser(Context context) {
		SharedPreferences prefs = context.getSharedPreferences("vcs_git_user", 0);
		
		username = prefs.getString("username", "");
		password = new char[0]; //We don't save passwords yet
		name = prefs.getString("name", "");
		email = prefs.getString("email", "");
	}
	
	public void saveUser(Context context) {
		SharedPreferences.Editor edit = context.getSharedPreferences("vcs_git_user", 0).edit();
		
		//Don't overwrite something with nothing
		if (username.length() > 0) edit.putString("username", username);
		if (name.length() > 0) edit.putString("name", name);
		if (email.length() > 0) edit.putString("email", email);
		
		edit.apply();
	}
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public char[] getPassword() {
		return password;
	}
	
	public void setPassword(char[] password) {
		this.password = password;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getEmail() {
		return email;
	}
	
	public void setEmail(String email) {
		this.email = email;
	}
}