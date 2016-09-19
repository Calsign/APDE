package com.calsignlabs.apde.learning;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;

import processing.data.XML;

public class CurriculumOverviewFragment extends Fragment {
	private View rootView;
	private SurfaceView surface;
	
	private Paint backgroundPaint;
	private Paint rectStrokePaint;
	private Paint arrowPaint;
	private Paint arrowheadPaint;
	
	private Paint[] rectFillPaints = new Paint[4];
	private TextPaint[] labelPaints = new TextPaint[4];
	private int[] stateMessageColors = new int[4];
	
	public static final int NOT_AVAILABLE = 0;
	public static final int LOCKED = 1;
	public static final int UNLOCKED = 2;
	public static final int COMPLETED = 3;
	
	private XML learningCurriculumOverviewData;
	private HashMap<String, NodeLabel> nodeLabels;
	
	private Rect displayRect;
	private PointF displayPointF1, displayPointF2;
	private Path displayPath;
	
	private GestureDetector gestureDetector;
	
	private float offsetX, offsetY;
	
	private float minOffsetX, maxOffsetX, minOffsetY, maxOffsetY;
	
	private FirebaseAnalytics firebaseAnalytics;
	
	protected class NodeLabel {
		protected String name;
		protected String title;
		protected String desc;
		protected String[] prerequisites;
		
		protected RectF rect;
		protected PointF point;
		
		protected float exactCenterY;
		
		protected int state;
		
		public NodeLabel(String name, String title, String desc, PointF point, RectF rect, String[] prerequisites, float exactCenterY) {
			this(name, title, desc, point, rect, prerequisites, exactCenterY, NOT_AVAILABLE);
		}
		
		public NodeLabel(String name, String title, String desc, PointF point, RectF rect, String[] prerequisites, float exactCenterY, int state) {
			this.name = name;
			this.title = title;
			this.desc = desc;
			this.point = point;
			this.rect = rect;
			this.prerequisites = prerequisites;
			this.exactCenterY = exactCenterY;
			this.state = state;
		}
		
		public String getStateString() {
			switch (state) {
				case NOT_AVAILABLE: return "not_available";
				case LOCKED: return "locked";
				case UNLOCKED: return "unlocked";
				case COMPLETED: return "completed";
				default: return "unknown_skill_tutorial_state";
			}
		}
	}
	
	public static CurriculumOverviewFragment newInstance(float width, float height) {
		CurriculumOverviewFragment fragment = new CurriculumOverviewFragment();
		Bundle args = new Bundle();
		args.putFloat("width", width);
		args.putFloat("height", height);
		fragment.setArguments(args);
		return fragment;
	}
	
	public CurriculumOverviewFragment() {
		backgroundPaint = new Paint();
		backgroundPaint.setStyle(Paint.Style.FILL);
		// Color set in onActivityCreated()
		
		rectStrokePaint = new Paint();
		rectStrokePaint.setStyle(Paint.Style.STROKE);
		rectStrokePaint.setStrokeWidth(4);
		rectStrokePaint.setAntiAlias(true);
		rectStrokePaint.setColor(Color.WHITE);
		
		for (int i = 0; i < rectFillPaints.length; i ++) {
			rectFillPaints[i] = new Paint();
			rectFillPaints[i].setStyle(Paint.Style.FILL);
			
			labelPaints[i] = new TextPaint();
			labelPaints[i].setTypeface(Typeface.create("Arial", Typeface.NORMAL));
			labelPaints[i].setStyle(TextPaint.Style.FILL);
			labelPaints[i].setTextAlign(TextPaint.Align.CENTER);
		}
		
		rectFillPaints[NOT_AVAILABLE].setColor(Color.BLACK);
		rectFillPaints[LOCKED].setColor(Color.GRAY);
		rectFillPaints[UNLOCKED].setColor(Color.parseColor("#69D3DE"));
		rectFillPaints[COMPLETED].setColor(Color.WHITE);
		
		labelPaints[NOT_AVAILABLE].setColor(Color.WHITE);
		labelPaints[LOCKED].setColor(Color.BLACK);
		labelPaints[UNLOCKED].setColor(Color.BLACK);
		labelPaints[COMPLETED].setColor(Color.BLACK);
		
		stateMessageColors[NOT_AVAILABLE] = Color.parseColor("#E83838");
		stateMessageColors[LOCKED] = Color.parseColor("#E83838");
		stateMessageColors[UNLOCKED] = Color.parseColor("#69D3DE");
		stateMessageColors[COMPLETED] = Color.WHITE;
		
		arrowPaint = new Paint();
		arrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		arrowPaint.setStrokeWidth(4);
		arrowPaint.setColor(Color.WHITE);
		arrowPaint.setAntiAlias(true);
		
		arrowheadPaint = new Paint();
		arrowheadPaint.setStyle(Paint.Style.FILL);
		arrowheadPaint.setColor(Color.WHITE);
		arrowheadPaint.setAntiAlias(true);
		
		displayRect = new Rect();
		displayPointF1 = new PointF();
		displayPointF2 = new PointF();
		displayPath = new Path();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// http://stackoverflow.com/a/23533575
		if (rootView == null) {
			rootView = inflater.inflate(R.layout.fragment_curriculum_overview, container, false);
		} else {
			((ViewGroup) rootView.getParent()).removeView(rootView);
		}
		
		return rootView;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());
		
		backgroundPaint.setColor(getActivity().getResources().getColor(R.color.activity_background));
		
		float width = getArguments().getFloat("width");
		float height = getArguments().getFloat("height");
		
		surface = (SurfaceView) rootView.findViewById(R.id.curriculum_map_surface);
		surface.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder surfaceHolder) {
				updateSurfaceView();
			}
			
			@Override
			public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {}
			
			@Override
			public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
				
			}
		});
		
		for (TextPaint labelPaint : labelPaints) {
			labelPaint.setTextSize(dpToPixels(14));
		}
		
		try {
			learningCurriculumOverviewData = new XML(new File(((APDE) getActivity().getApplication()).getLearningFolder(), "curriculum.xml"));
			
			// About every other node seems to be null... probably closing tags
			nodeLabels = new HashMap<>(learningCurriculumOverviewData.getChildCount() / 2);
			
			for (int i = 0; i < learningCurriculumOverviewData.getChildCount(); i ++) {
				XML node = learningCurriculumOverviewData.getChild(i);
				
				if (!node.getName().equals("node")) {
					continue;
				}
				
				String name = node.getString("name");
				String title = node.getChild("title").getString("value");
				String desc = node.getChild("desc").getString("value");
				String[] prerequisites = node.getString("prerequisite").split(",");
				
				labelPaints[0].getTextBounds(title, 0, title.length(), displayRect);
				
				float x = transformX(node.getChild("pos").getFloat("x"));
				float y = transformY(node.getChild("pos").getFloat("y"));
				float textWidth = labelPaints[0].measureText(title);
				float textHeight = displayRect.height();
				float padding = dpToPixels(10);
				
				nodeLabels.put(name, new NodeLabel(name, title, desc, new PointF(x, y),
						new RectF(x - textWidth / 2 - padding, y - textHeight / 2 - padding, x + textWidth / 2 + padding, y + textHeight / 2 + padding), prerequisites, displayRect.exactCenterY()));
			}
			
			updateBoundaries(width, height);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		gestureDetector = new GestureDetector(getActivity(), new GestureListener());
		
		surface.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				return gestureDetector.onTouchEvent(motionEvent);
			}
		});
		
		loadSkillTutorialProgress();
	}
	
	public void updateBoundaries(float width, float height) {
		if (nodeLabels == null) {
			// Something weird with activity restoring state
			return;
		}
		
		float buffer = dpToPixels(200);
		
		boolean firstIteration = true;
		
		for (NodeLabel nodeLabel : nodeLabels.values()) {
			if (firstIteration) {
				minOffsetX = -nodeLabel.point.x + width - buffer;
				maxOffsetX = -nodeLabel.point.x + buffer;
				minOffsetY = -nodeLabel.point.y + height - buffer;
				maxOffsetY = -nodeLabel.point.y + buffer;
				
				firstIteration = false;
			} else {
				minOffsetX = Math.min(minOffsetX, -nodeLabel.point.x + width - buffer);
				maxOffsetX = Math.max(maxOffsetX, -nodeLabel.point.x + buffer);
				minOffsetY = Math.min(minOffsetY, -nodeLabel.point.y + height - buffer);
				maxOffsetY = Math.max(maxOffsetY, -nodeLabel.point.y + buffer);
			}
		}
		
		if (minOffsetX > maxOffsetX) {
			// Doesn't fill the screen
			float dif  = minOffsetX - maxOffsetX;
			
			minOffsetX -= dif / 2;
			maxOffsetX += dif / 2;
		}
		
		if (minOffsetY > maxOffsetY) {
			// Doesn't fill the screen
			float dif = minOffsetY - maxOffsetY;
			
			minOffsetY -= dif / 2;
			maxOffsetY += dif / 2;
		}
		
		// Default position
		offsetX = width / 2;
		offsetY = dpToPixels(50);
		
		checkOffsetBoundaries();
	}
	
	protected SharedPreferences getLearningPreferences() {
		return getActivity().getSharedPreferences("learning", Context.MODE_PRIVATE);
	}
	
	protected void loadSkillTutorialProgress() {
		for (NodeLabel nodeLabel : nodeLabels.values()) {
			int state = getSkillTutorialState(nodeLabel.name);
			
			if (state != NOT_AVAILABLE) {
				nodeLabel.state = state;
			} else {
				if (!isSkillTutorialAvailable(nodeLabel.name)) {
					// Not available
					setSkillTutorialState(nodeLabel.name, NOT_AVAILABLE);
					nodeLabel.state = NOT_AVAILABLE;
				} else {
					setSkillTutorialState(nodeLabel.name, LOCKED);
					nodeLabel.state = LOCKED;
				}
			}
		}
		
		for (NodeLabel nodeLabel : nodeLabels.values()) {
			if (nodeLabel.state == LOCKED) {
				checkPrerequisites(nodeLabel);
			}
		}
	}
	
	protected void checkPrerequisites(NodeLabel nodeLabel) {
		boolean unlocked = true;
		
//		Log.d("debug", "checking prerequisites for " + nodeLabel.name);
		
		// Check prerequisites
		for (String prerequisite : nodeLabel.prerequisites) {
//			Log.d("debug", "checking prereq: " + prerequisite);
			
			// In case we have typos
			if (!nodeLabels.containsKey(prerequisite)) {
				if (!prerequisite.equals("")) {
					System.err.println("could not find key \"" + prerequisite + "\"");
				}
				continue;
			}
			
//			Log.d("debug", "prereq state: " + nodeLabels.get(prerequisite).state);
			
			if (nodeLabels.get(prerequisite).state != COMPLETED) {
				unlocked = false;
				break;
			}
		}
		
		// If all prerequisites completed (or no prerequisites), will be unlocked
		
		setSkillTutorialState(nodeLabel.name, unlocked ? UNLOCKED : LOCKED);
		nodeLabel.state = unlocked ? UNLOCKED : LOCKED;
	}
	
	protected int getSkillTutorialState(String name) {
		return getLearningPreferences().getInt("progress_" + name, NOT_AVAILABLE);
	}
	
	protected void setSkillTutorialState(String name, int state) {
		SharedPreferences.Editor edit = getLearningPreferences().edit();
		edit.putInt("progress_" + name, state);
		edit.commit();
		
		if (state == COMPLETED) {
			// Refresh to see if we have unlocked new tutorials
			loadSkillTutorialProgress();
		}
	}
	
	protected boolean isSkillTutorialAvailable(String name) {
		return Arrays.asList(new File(((APDE) getActivity().getApplication()).getLearningFolder(), "skills").list()).contains(name + ".xml");
	}
	
	protected void updateSurfaceView() {
		Canvas canvas = surface.getHolder().lockCanvas();
		displayCurriculumOverviewMap(canvas);
		surface.getHolder().unlockCanvasAndPost(canvas);
	}
	
	protected void displayCurriculumOverviewMap(Canvas canvas) {
		if (nodeLabels == null) {
			// Failed to load XML
			return;
		}
		
		canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), backgroundPaint);
		
		float radius = dpToPixels(5);
		
		canvas.translate(offsetX, offsetY);
		
		for (NodeLabel nodeLabel : nodeLabels.values()) {
			canvas.drawRoundRect(nodeLabel.rect, radius, radius, rectStrokePaint);
			canvas.drawRoundRect(nodeLabel.rect, radius, radius, rectFillPaints[nodeLabel.state]);
			
			canvas.drawText(nodeLabel.title, nodeLabel.point.x, nodeLabel.point.y - nodeLabel.exactCenterY, labelPaints[nodeLabel.state]);
			
			for (String prerequisite : nodeLabel.prerequisites) {
				// In case we have typos
				if (!nodeLabels.containsKey(prerequisite)) {
					if (!prerequisite.equals("")) {
						System.err.println("could not find key \"" + prerequisite + "\"");
					}
					continue;
				}
				
				NodeLabel target = nodeLabels.get(prerequisite);
				
				if (rectLineIntersection(displayPointF1, nodeLabel.rect, nodeLabel.point, target.point)
						&& rectLineIntersection(displayPointF2, target.rect, nodeLabel.point, target.point)) {
					
					canvas.drawLine(displayPointF1.x, displayPointF1.y, displayPointF2.x, displayPointF2.y, arrowPaint);
					drawArrowhead(canvas, displayPointF2, displayPointF1);
				}
			}
		}
	}
	
	protected boolean detectPress(float x, float y) {
		for (final NodeLabel nodeLabel : nodeLabels.values()) {
			if (nodeLabel.rect.contains(x - offsetX, y - offsetY)) {
				final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
				
				String readableState = getActivity().getResources().getStringArray(R.array.skill_tutorial_states)[nodeLabel.state];
				
				SpannableString message = new SpannableString(nodeLabel.desc + "\n\n" + readableState);
				message.setSpan(new ForegroundColorSpan(stateMessageColors[nodeLabel.state]), nodeLabel.desc.length() + 2, nodeLabel.desc.length() + 2 + readableState.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
				
				builder.setTitle(nodeLabel.title);
				builder.setMessage(message);
				
				builder.setPositiveButton(R.string.skill_tutorial_begin, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialogInterface, int i) {
						Bundle bundle = new Bundle();
						bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, nodeLabel.name);
						bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, nodeLabel.getStateString());
						firebaseAnalytics.logEvent("learning_begin_tutorial", bundle);
						
						((LearningActivity) getActivity()).loadSkillTutorial(nodeLabel.name, nodeLabel.title, nodeLabel.getStateString());
					}
				});
				
				AlertDialog dialog = builder.create();
				dialog.show();
				
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(nodeLabel.state == UNLOCKED || nodeLabel.state == COMPLETED); // TODO
				
				Bundle bundle = new Bundle();
				bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, nodeLabel.name);
				bundle.putString(FirebaseAnalytics.Param.ITEM_CATEGORY, nodeLabel.getStateString());
				firebaseAnalytics.logEvent("learning_view_tutorial", bundle);
				
				return true;
			}
		}
		
		return false;
	}
	
	private void drawArrowhead(Canvas canvas, PointF start, PointF end) {
		// With help from: http://stackoverflow.com/a/3501643/
		
		float length = dpToPixels(10);
		float height = dpToPixels(3);
		
		double theta = Math.atan2(end.y - start.y, end.x - start.x);
		double perpendicularTheta = Math.atan2(start.x - end.x, end.y - start.y);
		
		float baseX1 = end.x - (float) Math.cos(theta) * length + (float) Math.cos(perpendicularTheta) * height;
		float baseX2 = end.x - (float) Math.cos(theta) * length - (float) Math.cos(perpendicularTheta) * height;
		float baseY1 = end.y - (float) Math.sin(theta) * length + (float) Math.sin(perpendicularTheta) * height;
		float baseY2 = end.y - (float) Math.sin(theta) * length - (float) Math.sin(perpendicularTheta) * height;
		
		displayPath.rewind();
		
		displayPath.setFillType(Path.FillType.EVEN_ODD);
		displayPath.moveTo(end.x, end.y);
		displayPath.lineTo(baseX1, baseY1);
		displayPath.lineTo(baseX2, baseY2);
		displayPath.close();
		
		canvas.drawPath(displayPath, arrowheadPaint);
	}
	
	private float transformX(float x) {
		return dpToPixels(x) / 1.8f;
	}
	
	private float transformY(float y) {
		return dpToPixels(y) / 1.8f;
	}
	
	private boolean rectLineIntersection(PointF output, RectF rect, PointF start, PointF end) {
		return rectLineIntersection(output, rect, start.x, start.y, end.x, end.y);
	}
	
	private boolean rectLineIntersection(PointF output, RectF rect, float lineStartX, float lineStartY, float lineEndX, float lineEndY) {
		if (lineLineIntersection(output, rect.left, rect.top, rect.right, rect.top, lineStartX, lineStartY, lineEndX, lineEndY)) {
			return true;
		} else if (lineLineIntersection(output, rect.right, rect.top, rect.right, rect.bottom, lineStartX, lineStartY, lineEndX, lineEndY)) {
			return true;
		} else if (lineLineIntersection(output, rect.right, rect.bottom, rect.left, rect.bottom, lineStartX, lineStartY, lineEndX, lineEndY)) {
			return true;
		} else if (lineLineIntersection(output, rect.left, rect.bottom, rect.left, rect.top, lineStartX, lineStartY, lineEndX, lineEndY)) {
			return true;
		} else {
			return false;
		}
	}
	
	private boolean lineLineIntersection(PointF output, float line1StartX, float line1StartY, float line1EndX, float line1EndY, float line2StartX, float line2StartY, float line2EndX, float line2EndY) {
		// http://stackoverflow.com/a/16314158/
		
		if (line1StartX == line1EndX || line2StartX == line2EndX) {
			// One or more vertical lines
			
			if (line1StartX == line1EndX && line2StartX == line2EndX) {
				// Both lines are vertical
				
				// Ignore this corner case for our implementation
				return false;
			} else {
				// Only one line is vertical
				
				if (line1StartX == line1EndX) {
					// Line 1 is vertical
					
					float a2 = (line2EndY - line2StartY) / (line2EndX - line2StartX);
					float b2 = line2StartY - a2 * line2StartX;
					
					float y = a2 * line1StartX + b2;
					
					if (Math.min(line2StartX, line2EndX) < line1StartX && line1StartX < Math.max(line2StartX, line2EndX)
							&& Math.min(line1StartY, line1EndY) < y && y < Math.max(line1StartY, line1EndY)) {
						
						output.x = line1StartX;
						output.y = y;
						
						return true;
					} else {
						return false;
					}
				} else {
					// Line 2 is vertical
					
					float a1 = (line1EndY - line1StartY) / (line1EndX - line1StartX);
					float b1 = line1StartY - a1 * line1StartX;
					
					float y = a1 * line2StartX + b1;
					
					if (Math.min(line1StartX, line1EndX) < line2StartX && line2StartX < Math.max(line1StartX, line1EndX)
							&& Math.min(line2StartY, line2EndY) < y && y < Math.max(line2StartY, line2EndY)) {
						
						output.x = line2StartX;
						output.y = y;
						
						return true;
					} else {
						return false;
					}
				}
			}
		}
		
		// Calculate y=ax+b form of lines
		float a1 = (line1EndY - line1StartY) / (line1EndX - line1StartX);
		float b1 = line1StartY - a1 * line1StartX;
		float a2 = (line2EndY - line2StartY) / (line2EndX - line2StartX);
		float b2 = line2StartY - a2 * line2StartX;
		
		if (a1 == a2) {
			// Lines are parallel
			
			// Ignore this corner case for our implementation
			return false;
		}
		
		// Calculate x coordinate of intersection
		float x = -(b1 - b2) / (a1 - a2);
		
		// Determine if intersection is within segments
		if (Math.min(line1StartX, line1EndX) < x && x < Math.max(line1StartX, line1EndX) && Math.min(line2StartX, line2EndX) < x && x < Math.max(line2StartX, line2EndX)) {
			// Segments intersect!
			
			output.x = x;
			output.y = a1 * x + b1;
			
			return true;
		} else {
			return false;
		}
	}
	
	private float dpToPixels(float pixels) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pixels, getActivity().getResources().getDisplayMetrics());
	}
	
	@Override
	public void onDestroyView() {
		rootView = null;
		surface = null;
		
		gestureDetector = null;
		
		super.onDestroyView();
	}
	
	protected void checkOffsetBoundaries() {
		if (offsetX < minOffsetX) offsetX = minOffsetX;
		if (offsetX > maxOffsetX) offsetX = maxOffsetX;
		if (offsetY < minOffsetY) offsetY = minOffsetY;
		if (offsetY > maxOffsetY) offsetY = maxOffsetY;
	}
	
	protected class GestureListener extends GestureDetector.SimpleOnGestureListener {
		protected Handler handler = new Handler();
		
		@Override
		public boolean onDown(MotionEvent event) {
			return true;
		}
		
		@Override
		public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
			offsetX -= distanceX;
			offsetY -= distanceY;
			
			checkOffsetBoundaries();
			
			updateSurfaceView();
			
			return true;
		}
		
		@Override
		public boolean onFling(MotionEvent event1, MotionEvent event2, final float velocityX, final float velocityY) {
//			(new Runnable() {
//				private float count = 0;
//				
//				@Override
//				public void run() {
//					double mag = Math.pow(0.95, /*count*/0) * 0.01;
//					
//					offsetX -= velocityX * mag;
//					offsetY -= velocityY * mag;
//					
//					count ++;
//					
//					if (count > 50) {
//						handler.postDelayed(this, 10);
//					}
//				}
//			}).run();
			
			return true;
		}
		
		@Override
		public boolean onSingleTapConfirmed(MotionEvent event) {
			return detectPress(event.getX(), event.getY());
		}
	}
}
