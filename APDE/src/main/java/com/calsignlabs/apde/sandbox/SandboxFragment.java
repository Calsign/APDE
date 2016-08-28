package com.calsignlabs.apde.sandbox;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.calsignlabs.apde.R;
import com.calsignlabs.apde.SketchFile;
import com.calsignlabs.apde.build.Build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SandboxFragment extends Fragment {
	private View rootView;
	
	private WebView webView;
	private boolean pageLoaded = false;
	
	private OnPageLoadListener pageLoadListener = null;
	
	public static SandboxFragment newInstance() {
		return new SandboxFragment();
	}
	
	public SandboxFragment() {}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// http://stackoverflow.com/a/23533575
		if (rootView == null) {
			rootView = inflater.inflate(R.layout.fragment_sandbox, container, false);
		} else {
			((ViewGroup) rootView.getParent()).removeView(rootView);
		}
		
		return rootView;
	}
	
	@Override
	@SuppressLint("SetJavaScriptEnabled")
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		webView = (WebView) getView().findViewById(R.id.sandbox_webview);
		
		copySandboxFiles();
		
		webView.getSettings().setJavaScriptEnabled(true);
		webView.setWebChromeClient(new WebChromeClient() {
			@Override
			public boolean onConsoleMessage(ConsoleMessage message) {
				if (message.message().startsWith("APDE System.out: ")) {
					// println() or print() from sketch
					System.out.println(message.message().length() > 17 ? message.message().substring(17) : "");
				} else {
					// An error message, or something else
					System.out.println(message.message() + " -- line # " + message.lineNumber() + " of " + message.sourceId());
				}
				
				return true;
			}
		});
		// http://stackoverflow.com/a/17815721/
		webView.setWebViewClient(new WebViewClient() {
			private int counter = 0;
			
			@Override
			public boolean shouldOverrideUrlLoading(WebView webView, String urlNewString) {
				counter ++;
				webView.loadUrl(urlNewString);
				return true;
			}
			
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				counter = Math.max(counter, 1);
			}
			
			@Override
			public void onPageFinished(WebView view, String url) {
				if (--counter == 0) {
					pageLoaded = true;
					
					if (pageLoadListener != null) {
						pageLoadListener.onPageLoad();
					}
				}
			}
		});
		webView.setInitialScale(100);
		webView.getSettings().setSupportZoom(false);
		webView.loadUrl("file:///" + getIndexFile().getAbsolutePath());
	}
	
	@Override
	public void onDestroyView() {
		// Kill the sketch...
		// TODO some things might still be running? hopefully not...
		killSketch();
		
		webView = null;
		
		super.onDestroyView();
	}
	
	public void killSketch() {
		webView.loadUrl("javascript:kill()");
	}
	
	private File getSandboxFolder() {
		return getActivity().getDir("sandbox", Activity.MODE_PRIVATE);
	}
	
	private File getIndexFile() {
		return new File(getSandboxFolder(), "index.html");
	}
	
	private void copySandboxFiles() {
		String folder = "sandbox/";
		String[] files = {"index.html", "processing.js", "controller.js"};
		
		try {
			// Re-write folder every time
			if (getSandboxFolder().exists()) {
				// For some reason this doesn't seem to be working...
				Build.deleteFile(getSandboxFolder());
			}
			
			if (!getSandboxFolder().exists()) {
				getSandboxFolder().mkdir();
			}
			
			for (String file : files) {
				File dest = new File(getSandboxFolder(), file);
				
				InputStream inputStream = getActivity().getAssets().open(folder + file);
				Build.createFileFromInputStream(inputStream, dest);
				inputStream.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void updateCode(SketchFile[] sketchFiles) {
		String pdeCode = compileSketchFiles(sketchFiles);
		String escapedPdeCode = pdeCode.replace("\"", "\\\"").replace("\n", "\\n");
		
		final String jsFunction = "updateCode(\"" + escapedPdeCode + "\")";
		
		if (pageLoaded) {
			webView.loadUrl("javascript:" + jsFunction);
		} else {
			pageLoadListener = new OnPageLoadListener() {
				@TargetApi(android.os.Build.VERSION_CODES.KITKAT)
				@Override
				public void onPageLoad() {
					webView.loadUrl("javascript:" + jsFunction);
				}
			};
		}
	}
	
	private String compileSketchFiles(SketchFile[] sketchFiles) {
		StringBuilder code = new StringBuilder();
		
		int bigCount = 0;
		for (SketchFile sketchFile : sketchFiles) {
			if (sketchFile.getSuffix().equals(".pde")) {
				sketchFile.setPreprocOffset(bigCount);
				code.append(sketchFile.getText());
				code.append("\n");
				bigCount += numLines(sketchFile.getText());
			}
		}
		
		return code.toString();
	}
	
	private int numLines(String input) {
		int count = 1;
		
		for (int i = 0; i < input.length(); i ++) {
			if (input.charAt(i) == '\n') {
				count++;
			}
		}
		
		return count;
	}
	
	public interface OnPageLoadListener {
		public void onPageLoad();
	}
}
