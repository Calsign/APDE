package com.calsignlabs.apde.learning;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.calsignlabs.apde.R;
import com.google.firebase.analytics.FirebaseAnalytics;

public class SkillTutorialFragment extends Fragment {
	private View rootView;
	private ScrollView scroller;
	private LinearLayout container;
	
	private Button backButton, nextButton, finishButton;
	
	private SkillTutorial skillTutorial;
	private String skillTutorialStateString;
	private int currentPage = -1;
	
	private FirebaseAnalytics firebaseAnalytics;
	
	public static SkillTutorialFragment newInstance(String skillTutorialName, String skillTutorialStateString) {
		SkillTutorialFragment skillTutorialFragment = new SkillTutorialFragment();
		Bundle args = new Bundle();
		args.putString("skillTutorialName", skillTutorialName);
		args.putString("skillTutorialStateString", skillTutorialStateString);
		skillTutorialFragment.setArguments(args);
		return skillTutorialFragment;
	}
	
	public SkillTutorialFragment() {}
	
	protected FirebaseAnalytics getAnalytics() {
		return firebaseAnalytics;
	}
	
	protected SkillTutorial getSkillTutorial() {
		return skillTutorial;
	}
	
	protected String getSkillTutorialStateString() {
		return skillTutorialStateString;
	}
	
	protected int getCurrentPage() {
		return currentPage;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// http://stackoverflow.com/a/23533575
		if (rootView == null) {
			rootView = inflater.inflate(R.layout.fragment_skill_tutorial, container, false);
		} else {
			((ViewGroup) rootView.getParent()).removeView(rootView);
		}
		
		return rootView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());
		
		container = (LinearLayout) rootView.findViewById(R.id.skill_tutorial_container);
		scroller = (ScrollView) rootView.findViewById(R.id.skill_tutorial_container_scroller); 
		
		skillTutorial = new SkillTutorial(getActivity(), this, getArguments().getString("skillTutorialName"));
		skillTutorialStateString = getArguments().getString("skillTutorialStateString");
		
		backButton = (Button) rootView.findViewById(R.id.skill_tutorial_back);
		nextButton = (Button) rootView.findViewById(R.id.skill_tutorial_next);
		finishButton = (Button) rootView.findViewById(R.id.skill_tutorial_finish);
		
		if (currentPage != -1) {
			loadPage(currentPage);
		} else {
			if (savedInstanceState != null) {
				int savedPage = savedInstanceState.getInt("currentPage", 0);
				
				if (savedPage != -1) {
					loadPage(savedPage);
				} else {
					loadPage(0);
				}
			} else {
				loadPage(0);
			}
		}
		
		backButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (currentPage > 0) {
					loadPage(currentPage - 1);
				}
			}
		});
		
		nextButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (currentPage < skillTutorial.getPageCount() - 1) {
					loadPage(currentPage + 1);
				}
			}
		});
		
		finishButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (currentPage < skillTutorial.getPageCount() - 1) {
					// Shouldn't happen *grumble grumble*
					return;
				}
				
				Bundle bundle = new Bundle();
				bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, skillTutorial.getName());
				bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, skillTutorialStateString);
				firebaseAnalytics.logEvent("learning_finish_tutorial", bundle);
				
				((LearningActivity) getActivity()).finishSkillTutorial(skillTutorial.getName());
			}
		});
	}
	
	public FrameLayout getRootView() {
		return (FrameLayout) rootView;
	}
	
	public void loadPage(int page) {
		skillTutorial.loadPage(container, scroller, page, currentPage);
		currentPage = page;
		
		boolean canBack = page > 0;
		boolean canNext = page < skillTutorial.getPageCount() - 1;
		
		backButton.setEnabled(canBack);
		nextButton.setEnabled(canNext);
		
		backButton.getCompoundDrawables()[0].setAlpha(canBack ? 255 : 51);
		nextButton.getCompoundDrawables()[2].setAlpha(canNext ? 255 : 51);
		
		nextButton.setVisibility(canNext ? View.VISIBLE : View.GONE);
		finishButton.setVisibility(canNext ? View.GONE : View.VISIBLE);
		
		Bundle bundle = new Bundle();
		bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, skillTutorial.getName());
		bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, skillTutorialStateString);
		bundle.putLong(FirebaseAnalytics.Param.LEVEL, page);
		firebaseAnalytics.logEvent("learning_tutorial_navigate_page", bundle);
	}
	
	@Override
	public void onSaveInstanceState(Bundle icicle) {
		super.onSaveInstanceState(icicle);
		
		icicle.putInt("currentPage", currentPage);
	}
	
	@Override
	public void onDestroyView() {
		rootView = null;
		container = null;
		
		super.onDestroyView();
	}
}
