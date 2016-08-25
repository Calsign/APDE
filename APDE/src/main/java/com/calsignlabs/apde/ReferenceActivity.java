package com.calsignlabs.apde;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.util.Arrays;

public class ReferenceActivity extends AppCompatActivity {
	private WebView webView;
	private Spinner referenceSource;
	
	private String[] uris = {"http://processing.org/reference/", "http://android.processing.org/reference/"};
	
	private String[] baseUris = {"http://processing.org/", "http://android.processing.org/"};
	private String[] baseUrisHttps = {"https://processing.org/", "https://android.processing.org/"};
	
	private boolean FLAG_UPDATE_REFERENCE = false;
	private boolean FLAG_FIRST_LOAD = true;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_reference);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
		
		// Use the spinner instead
		getSupportActionBar().setDisplayShowTitleEnabled(false);
		
		referenceSource = (Spinner) findViewById(R.id.reference_source_spinner);
		
		final CharSequence[] titles = {getResources().getString(R.string.reference_source_processing), getResources().getString(R.string.reference_source_android_processing)};
		
		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Arrays.asList(titles));
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		referenceSource.setAdapter(adapter);
		
		String uri = getIntent().getStringExtra("uri");
		
		if (uri == null) {
			// Default to the Processing Reference main page if no specific page is specified
			// This is what happens behind the "Open Reference" tool
			uri = uris[0];
		}
		
		webView = (WebView) findViewById(R.id.reference_webview);
		webView.loadUrl(uri);
		webView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageFinished(WebView view, String url) {
//				Log.d("debug", "loaded page: " + url);
				updateReferenceSourceSpinnerSelection();
			}
		});
		
		webView.getSettings().setBuiltInZoomControls(true);
		webView.getSettings().setDisplayZoomControls(false);
		
		updateReferenceSourceSpinnerSelection();
		
		referenceSource.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
				if (FLAG_UPDATE_REFERENCE) {
					FLAG_UPDATE_REFERENCE = false;
				} else if (FLAG_FIRST_LOAD) {
					FLAG_FIRST_LOAD = false;
				} else {
//					Log.d("debug", "loading uri: " + uris[i]);
					webView.loadUrl(uris[i]);
				}
			}
			
			@Override
			public void onNothingSelected(AdapterView<?> adapterView) {}
		});
	}
	
	public void updateReferenceSourceSpinnerSelection() {
		String uri = webView.getUrl();
		
		int index = 0;
		for (int i = 0; i < uris.length; i ++) {
			if (uri.startsWith(baseUris[i]) || uri.startsWith(baseUrisHttps[i])) {
				index = i;
				break;
			}
		}
		
		if (referenceSource.getSelectedItemPosition() != index) {
			FLAG_UPDATE_REFERENCE = true;
//			Log.d("debug", "updating reference");
			referenceSource.setSelection(index);
		}
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
			webView.goBack();
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_reference, menu);
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			case R.id.action_open_uri_external:
				openUriExternal();
				return true;
			case R.id.action_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	public void openUriExternal() {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(webView.getUrl()));
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}
}
