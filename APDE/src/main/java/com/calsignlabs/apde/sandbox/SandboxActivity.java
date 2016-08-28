package com.calsignlabs.apde.sandbox;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.view.WindowManager;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;

public class SandboxActivity extends AppCompatActivity {
	private SandboxFragment sandboxFragment;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Hide title bar
		// http://stackoverflow.com/a/2591311/
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		setContentView(R.layout.activity_sandbox);
		
		sandboxFragment = SandboxFragment.newInstance();
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.add(R.id.sandbox_container, sandboxFragment);
		transaction.commit();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		sandboxFragment.updateCode(((APDE) getApplication()).getEditor().getTabMetas());
		((APDE) getApplication()).getEditor().clearConsole();
	}
	
	@Override
	public void onDestroy() {
		sandboxFragment = null;
		
		super.onDestroy();
	}
}
