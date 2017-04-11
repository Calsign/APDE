package com.calsignlabs.apde;

import android.content.Context;
import android.graphics.Point;
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
		
		if (getGlobalState().getPref("enable_voice_input", false)) {
			code.setPrivateImeOptions("");
		} else {
			code.setPrivateImeOptions("nm");
		}
		
		//Correctly size the code area
		
		int fullWidth;
		
		Point point = new Point();
		getActivity().getWindowManager().getDefaultDisplay().getSize(point);
		fullWidth = point.x;
		
		int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()) * 2;
		
		int minWidth = fullWidth - padding;
		
		getCodeEditText().setMinWidth(minWidth);
		
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
		
		codeScroller.setOnTouchListener(new View.OnTouchListener() {
			private static final int MAX_CLICK_DURATION = 200;
			private static final int MAX_DISTANCE = 10;
			
			private float initialX, initialY;
			
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				int maxDist = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MAX_DISTANCE, getResources().getDisplayMetrics());
				
				switch (motionEvent.getAction()) {
				case MotionEvent.ACTION_UP:
					if (motionEvent.getEventTime() - motionEvent.getDownTime() <= MAX_CLICK_DURATION
							&& sqDistLessThan(motionEvent.getX(), motionEvent.getY(), initialX, initialY, maxDist)) {
						if (!getGlobalState().isExample()) {
							// Make the keyboard visible (if the user doesn't have a hardware keyboard)
							if (!getGlobalState().getEditor().keyboardVisible && !PreferenceManager.getDefaultSharedPreferences(getGlobalState()).getBoolean("use_hardware_keyboard", false)) {
								InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
								imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
							}
							
							// Set selection
							getCodeEditText().setSelection(getCodeEditText().getText().length());
							getCodeEditText().requestFocus();
						}
					}
					break;
				case MotionEvent.ACTION_DOWN:
					initialX = motionEvent.getX();
					initialY = motionEvent.getY();
					break;
				}
				
				return false;
			}
			
			private boolean sqDistLessThan(float x1, float y1, float x2, float y2, float dist) {
				return (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) <= dist * dist;
			}
		});
		
		updateWithSketchFile();
		sketchFile.updateEditor(getGlobalState().getEditor());
		
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
	
	public boolean isInitialized() {
		return code != null;
	}
	
	public void setSketchFile(SketchFile sketchFile) {
		this.sketchFile = sketchFile;
	}
	
	public void updateWithSketchFile() {
		getCodeEditText().setText(sketchFile.getText());
		
		boolean editable = !sketchFile.isExample();
		
		getCodeEditText().setFocusable(editable);
		getCodeEditText().setFocusableInTouchMode(editable);
	}
}
