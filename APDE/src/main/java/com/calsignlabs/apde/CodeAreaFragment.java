package com.calsignlabs.apde;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

public class CodeAreaFragment extends Fragment {
//	protected OnFragmentInteractionListener mListener;
	
	protected SketchFile sketchFile;
	
	protected CodeEditText code;
	protected ScrollView codeScroller;
	protected HorizontalScrollView codeScrollerX;
	
	public static CodeAreaFragment newInstance(SketchFile sketchFile) {
		CodeAreaFragment fragment = new CodeAreaFragment();
		Bundle args = new Bundle();
		args.putParcelable("sketchFile", sketchFile);
		fragment.setArguments(args);
		
		return fragment;
	}
	
	public CodeAreaFragment() {}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (getArguments() != null) {
			sketchFile = (SketchFile) getArguments().getParcelable("sketchFile");
		}
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		//Correctly size the code area
		
		int minWidth;
		int maxWidth;
		
		//Let's try and do things correctly for once
		if (android.os.Build.VERSION.SDK_INT >= 13) {
			Point point = new Point();
			getActivity().getWindowManager().getDefaultDisplay().getSize(point);
			maxWidth = point.x;
		} else {
			maxWidth = getActivity().getWindowManager().getDefaultDisplay().getWidth();
		}
		
		minWidth = maxWidth - (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()) * 2;
		
		getCodeEditText().setMinimumWidth(minWidth);
		codeScrollerX.getLayoutParams().width = maxWidth;
		codeScroller.getLayoutParams().width = maxWidth;
		
		//Detect touch events
		getCodeEditText().setOnTouchListener(new EditText.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				//Disable the soft keyboard if the user is using a hardware keyboard
				if (PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).getBoolean("use_hardware_keyboard", false)) {
					getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
				}
				
				return false;
			}
		});
		
		//Forward touch events to the code area so that the user can select anywhere
		codeScroller.setOnTouchListener(new View.OnTouchListener() {
			//Meta data from the current touch event
//        	private boolean dragged = false;
//        	private float startX, startY;
			private MotionEvent startEvent;
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (getGlobalState().isExample()) {
					return false;
				}
				
				//Make the keyboard visible (if the user doesn't have a hardware keyboard)
				if (!getGlobalState().getEditor().keyboardVisible && !PreferenceManager.getDefaultSharedPreferences(getGlobalState()).getBoolean("use_hardware_keyboard", false)) {
					InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
				}
				
				//Make sure that the scroll area can still scroll
				return false;
			}
		});
		
		final View activityRootView = getGlobalState().getEditor().findViewById(R.id.content);
		
		getCodeEditText().setMinimumWidth(activityRootView.getWidth());
		getCodeEditText().setMinWidth(activityRootView.getWidth());
		
		getCodeEditText().setText(sketchFile.getText());
		
		boolean editable = !sketchFile.isExample();
		
		getCodeEditText().setFocusable(editable);
		getCodeEditText().setFocusableInTouchMode(editable);
		
		getCodeEditText().setupCustomActionMode();
		getCodeEditText().setupTextListener();
		
		getCodeEditText().refreshTextSize();
		getCodeEditText().flagRefreshTokens();
	}
	
	public CodeEditText getCodeEditText() {
		return code;
	}
	
	public ScrollView getCodeScroller() {
		return codeScroller;
	}
	
	public HorizontalScrollView getCodeScrollerX() {
		return codeScrollerX;
	}
	
	public APDE getGlobalState() {
		return (APDE) getActivity().getApplication();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_code_area, container, false);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		code = (CodeEditText) getView().findViewById(R.id.code);
		codeScroller = (ScrollView) getView().findViewById(R.id.code_scroller);
		codeScrollerX = (HorizontalScrollView) getView().findViewById(R.id.code_scroller_x);
	}
	
	@Override
	public void onDestroyView() {
		// Make sure we save our state
		sketchFile.update(getGlobalState().getEditor(), getGlobalState().getPref("pref_key_undo_redo", true));
		
		// Don't want to leak the Activity!
		code = null;
		codeScroller = null;
		codeScrollerX = null;
		
		super.onDestroyView();
	}

//	@Override
//	public void onAttach(Activity activity) {
//		super.onAttach(activity);
//		
//		try {
//			mListener = (OnFragmentInteractionListener) activity;
//		} catch (ClassCastException e) {
//			throw new ClassCastException(activity.toString()
//					+ " must implement OnFragmentInteractionListener");
//		}
//	}
//
//	@Override
//	public void onDetach() {
//		super.onDetach();
//		
//		mListener = null;
//	}
//	
//	public interface OnFragmentInteractionListener {
//		void onFragmentInteraction();
//	}
}
