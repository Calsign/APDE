package com.calsignlabs.apde.learning;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;

import com.calsignlabs.apde.R;
import com.calsignlabs.apde.SettingsActivity;
import com.calsignlabs.apde.sandbox.SandboxFragment;
import com.google.firebase.analytics.FirebaseAnalytics;

public class LearningActivity extends AppCompatActivity {
	private CurriculumOverviewFragment curriculumOverviewFragment;
	private SkillTutorialFragment skillTutorialFragment;
	private SandboxFragment sandboxFragment;
	
	private FirebaseAnalytics firebaseAnalytics;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_learning);
		
		firebaseAnalytics = FirebaseAnalytics.getInstance(this);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		final FrameLayout container = (FrameLayout) findViewById(R.id.learning_container);
		
		if (savedInstanceState == null) {
			container.requestLayout();
			container.post(new Runnable() {
				@Override
				public void run() {
					curriculumOverviewFragment = CurriculumOverviewFragment.newInstance(container.getWidth(), container.getHeight());
					
					FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
					transaction.add(R.id.learning_container, curriculumOverviewFragment);
					transaction.commit();
				}
			});
		} else {			
			if (savedInstanceState.containsKey("curriculumOverview")) curriculumOverviewFragment = (CurriculumOverviewFragment) getSupportFragmentManager().getFragment(savedInstanceState, "curriculumOverview");
			if (savedInstanceState.containsKey("skillTutorial")) skillTutorialFragment = (SkillTutorialFragment) getSupportFragmentManager().getFragment(savedInstanceState, "skillTutorial");
			if (savedInstanceState.containsKey("sandbox")) sandboxFragment = (SandboxFragment) getSupportFragmentManager().getFragment(savedInstanceState, "sandbox");
			
			if (curriculumOverviewFragment != null) {
				container.requestLayout();
				container.post(new Runnable() {
					@Override
					public void run() {
						curriculumOverviewFragment.updateBoundaries(container.getWidth(), container.getHeight());
					}
				});
			}
		}
		
		Bundle bundle = new Bundle();
		bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "learning");
		firebaseAnalytics.logEvent("open_screen", bundle);
	}
	
	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		
		if (curriculumOverviewFragment != null) getSupportFragmentManager().putFragment(icicle, "curriculumOverview", curriculumOverviewFragment);
		if (skillTutorialFragment != null) getSupportFragmentManager().putFragment(icicle, "skillTutorial", skillTutorialFragment);
		if (sandboxFragment != null) getSupportFragmentManager().putFragment(icicle, "sandbox", sandboxFragment);
	}
	
	public void loadSkillTutorial(String name, String title, String stateString) {
		skillTutorialFragment = SkillTutorialFragment.newInstance(name, stateString);
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.remove(curriculumOverviewFragment);
		transaction.add(R.id.learning_container, skillTutorialFragment);
		transaction.addToBackStack(null);
		transaction.commit();
		
		getSupportActionBar().setTitle(title);
	}
	
	public void finishSkillTutorial(String name) {
		curriculumOverviewFragment.setSkillTutorialState(name, CurriculumOverviewFragment.COMPLETED);
		getSupportFragmentManager().popBackStack();
		skillTutorialFragment = null;
	}
	
	public void loadExampleCode(String code) {
		sandboxFragment = SandboxFragment.newInstance();
		
		FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
		transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		transaction.remove(skillTutorialFragment);
		transaction.add(R.id.learning_container, sandboxFragment);
		transaction.addToBackStack(null);
		transaction.commit();
		
		getSupportFragmentManager().executePendingTransactions();
		
		sandboxFragment.updateCode(code);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_learning, menu);
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				navigateBack();
				return true;
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	public void onBackPressed() {
		navigateBack();
	}
	
	protected void navigateBack() {
		if (sandboxFragment != null) {
			sandboxFragment = null;
			getSupportFragmentManager().popBackStack();
		} else if (skillTutorialFragment != null) {
			skillTutorialFragment = null;
			getSupportFragmentManager().popBackStack();
			getSupportActionBar().setTitle(R.string.title_activity_learning);
		} else {
			finish();
		}
	}
	
	@Override
	public void onDestroy() {
		curriculumOverviewFragment = null;
		skillTutorialFragment = null;
		sandboxFragment = null;
		
		super.onDestroy();
	}
}
