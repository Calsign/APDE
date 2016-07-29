package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.calsignlabs.apde.contrib.ContributionManager;
import com.calsignlabs.apde.contrib.Library;
import com.calsignlabs.apde.support.CustomProgressDialog;
import com.ipaulpro.afilechooser.utils.FileUtils;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LibraryManagerActivity extends AppCompatActivity {
	//All potential archive / compressed file extensions (hopefully) TODO Yes, I realize that the list currently contains one item.
	public static String[] zipExtensions = {".zip"};
	
	//This is a number, that's all that matters
	private static final int REQUEST_CHOOSER = 6283;
	
	//File select request codes for the DX Dexer tool
	private static final int DX_DEXER_SELECT_INPUT_FILE = 7501;
	private static final int DX_DEXER_SELECT_OUTPUT_FILE = 7502;
	
	private EditText dxDexerInputFile;
	private EditText dxDexerOutputFile;
	private Button dxDexerDexButton;
	private TextView dxDexerErrorMessage;
	
	//Whether or not we are currently doing something
	private boolean working = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_library_manager);
		
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setBackgroundColor(getResources().getColor(R.color.bar_overlay));
		setSupportActionBar(toolbar);
		
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.activity_background));
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		refreshLibraryList();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_CHOOSER:
			if(resultCode == RESULT_OK) {
				final Uri uri = data.getData();
				
				//Get the File path from the Uri
				String path = FileUtils.getPath(this, uri);
				
				if(path != null && FileUtils.isLocal(path)) {
					File file = new File(path);
					if(file.exists()) {
						if(isZipExtension(FileUtils.getExtension(path))) {
							//Make sure that this library doesn't already exist...
							if(new Library(ContributionManager.detectLibraryName(file)).getLibraryFolder((APDE) getApplicationContext()).exists()) {
								alert(getResources().getString(R.string.invalid_file_error_title), getResources().getString(R.string.invalid_file_error_message_3));
							} else {
								addZipLibrary(file);
							}
						} else {
							alert(getResources().getString(R.string.invalid_file_error_title), getResources().getString(R.string.invalid_file_error_message_2));
						}
					} else {
						alert(getResources().getString(R.string.invalid_file_error_title), getResources().getString(R.string.invalid_file_error_message_1));
					}
				} else {
					alert(getResources().getString(R.string.invalid_file_error_title), getResources().getString(R.string.invalid_file_error_message_1));
				}
			}
			break;
		case DX_DEXER_SELECT_INPUT_FILE:
			if(resultCode == RESULT_OK) {
				final Uri uri = data.getData();
				
				//Get the File path from the Uri
				String path = FileUtils.getPath(this, uri);
				
				if(path != null && FileUtils.isLocal(path)) {
					File file = new File(path);
					
					dxDexerInputFile.setText(file.getAbsolutePath());
				}
			}
			
			break;
		case DX_DEXER_SELECT_OUTPUT_FILE:
			if(resultCode == RESULT_OK) {
				final Uri uri = data.getData();
				
				//Get the File path from the Uri
				String path = FileUtils.getPath(this, uri);
				
				if(path != null && FileUtils.isLocal(path)) {
					File file = new File(path);

					dxDexerOutputFile.setText(file.getAbsolutePath());
				}
			}
			
			break;
		}
	}
	
	private void alert(String title, String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(title);
		builder.setMessage(message);
		
		builder.setCancelable(true);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		builder.create().show();
	}
	
	public static boolean isZipExtension(String ext) {
		for(String zip : zipExtensions) {
			if(zip.equals(ext)) {
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.library_manager, menu);
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
            	finish();
                return true;
            case R.id.action_install_zip_library:
            	launchInstallZipLibrary();
            	return true;
            case R.id.action_get_libraries:
            	launchGetLibraries();
            	return true;
            case R.id.action_settings:
            	launchSettings();
            	return true;
            case R.id.action_dx_dexer_tool:
            	launchDexerTool();
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
	
	private void launchInstallZipLibrary() {
		//Launch file selection intent (includes AFileChooser's custom file chooser implementation)
		
		Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), getResources().getString(R.string.select_file));
		startActivityForResult(intent, REQUEST_CHOOSER);
	}
	
	private void launchGetLibraries() {
		//Open a browser to Processing's libraries page so that the user can download libraries manually
		//This is until we figure out what to do about downloading and installing libraries directly
		
		Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://processing.org/reference/libraries/"));
		startActivity(browserIntent);
	}
	
	@SuppressLint("InlinedApi")
	private void launchDexerTool() {
		//Show a DX Dexer dialog
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.action_dx_dexer_tool);		
		
		ScrollView layout;
		
		layout = (ScrollView) View.inflate(new ContextThemeWrapper(this, R.style.Theme_AppCompat_Dialog), R.layout.dx_dexer_tool, null);
		
		dxDexerInputFile = (EditText) layout.findViewById(R.id.dx_dexer_input_file);
		dxDexerOutputFile = (EditText) layout.findViewById(R.id.dx_dexer_output_file);
		
		dxDexerErrorMessage = (TextView) layout.findViewById(R.id.dx_dexer_error_message);
		
		((ImageButton) layout.findViewById(R.id.dx_dexer_input_file_select)).setOnClickListener(new ImageButton.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), getResources().getString(R.string.dx_dexer_select_input_file));
			    startActivityForResult(intent, DX_DEXER_SELECT_INPUT_FILE);
			}
		});
		
		((ImageButton) layout.findViewById(R.id.dx_dexer_output_file_select)).setOnClickListener(new ImageButton.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = Intent.createChooser(FileUtils.createGetContentIntent(), getResources().getString(R.string.dx_dexer_select_output_file));
			    startActivityForResult(intent, DX_DEXER_SELECT_OUTPUT_FILE);
			}
		});
		
		builder.setView(layout);
		
		builder.setPositiveButton(R.string.dex, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dxDexerRun();
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		final AlertDialog dialog = builder.create();
		dialog.show();
		
		dxDexerDexButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		dxDexerDexButton.setEnabled(false);
		
		dxDexerInputFile.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				validateDxDexerFiles();
			}
			
			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
			
			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
		});
		
		dxDexerOutputFile.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				validateDxDexerFiles();
			}
			
			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
			
			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
		});
		
		dxDexerDexButton.setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(View v) {
				dialog.dismiss();
				dxDexerRun();
			}
		});
		
		validateDxDexerFiles();
	}
	
	private void validateDxDexerFiles() {
		dxDexerDexButton.setEnabled(false);
		
		String inputPath = dxDexerInputFile.getText().toString();
		String outputPath = dxDexerOutputFile.getText().toString();
		
		File input = new File(inputPath);
		File output = new File(outputPath);
		
		if (inputPath.length() == 0) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_enter_input_file);
			return;
		}
		
		if (!input.exists()) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_input_file_exist);
			return;
		}
		
		if (input.isDirectory()) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_input_file_directory);
			return;
		}
		
		if (!(inputPath.endsWith(".jar")
				|| inputPath.endsWith(".zip")
				|| inputPath.endsWith(".apk"))) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_input_extension);
			return;
		}
		
		if (outputPath.length() == 0) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_enter_output_file);
			return;
		}
		
		if (output.exists()) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_output_file_exist);
			return;
		}
		
		if (!(outputPath.endsWith(".jar")
				|| outputPath.endsWith(".dex")
				|| outputPath.endsWith(".zip")
				|| outputPath.endsWith(".apk"))) {
			dxDexerErrorMessage.setText(R.string.dx_dexer_error_output_extension);
			return;
		}
		
		dxDexerErrorMessage.setText(R.string.dx_dexer_ready);
		
		dxDexerDexButton.setEnabled(true);
	}
	
	private void dxDexerRun() {
		final File inputFile = new File(dxDexerInputFile.getText().toString());
		final File outputFile = new File(dxDexerOutputFile.getText().toString());
		
		final CustomProgressDialog dialog = new CustomProgressDialog(this, View.GONE, View.GONE);
		
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setTitle(getResources().getString(R.string.dx_dexer_tool_dialog_title) + " " + inputFile.getName());
		dialog.setCanceledOnTouchOutside(false);
		dialog.setCancelable(false);
		
		final Thread dexThread = new Thread(new Runnable() {
			public void run() {
				working = true;
				ContributionManager.dexJar(inputFile, outputFile);
				working = false;
				
				dialog.dismiss();
			}
		});
		
		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialogInterface) {
				//The user cancelled... hopefully this won't cause problems
				dexThread.interrupt();
				working = false;
				
				//Undo any progress
				outputFile.delete();
			}
		});
		
		dexThread.start();
		dialog.show();
		
		dialog.setProgressText(getResources().getString(R.string.dexing) + "...");
	}
	
	private void launchSettings() {
		startActivity(new Intent(this, SettingsActivity.class));
	}
	
	public void refreshLibraryList() {
		final ListView libList = (ListView) findViewById(R.id.library_manager_list);
		final LibraryManagerAdapter adapter = new LibraryManagerAdapter(this, R.layout.library_manager_list_item, ((APDE) getApplicationContext()).getLibraries());
		
		libList.setAdapter(adapter);
		
		adapter.notifyDataSetChanged();
		
		//If there aren't any libraries, let the user know
		if(adapter.getCount() <= 0) {
			findViewById(R.id.library_manager_empty).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.library_manager_empty).setVisibility(View.GONE);
		}
	}
	
	public class LibraryManagerAdapter extends ArrayAdapter<Library> {
		public LibraryManagerAdapter(Context context, int resource) {
			super(context, resource);
		}
		
		public LibraryManagerAdapter(Context context, int resource, List<Library> items) {
			super(context, resource, items);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			
			if(view == null) {
				LayoutInflater inflater;
				inflater = LayoutInflater.from(getContext());
				view = inflater.inflate(R.layout.library_manager_list_item, null);
			}
			
			final Library lib = getItem(position);
			
			if(view != null && view instanceof RelativeLayout) {
				RelativeLayout container = (RelativeLayout) view;
				TextView title = (TextView) container.findViewById(R.id.library_manager_list_item_title);
				TextView author = (TextView) container.findViewById(R.id.library_manager_list_item_author);
				TextView desc = (TextView) container.findViewById(R.id.library_manager_list_item_desc);
				
				title.setText(Html.fromHtml(toHtmlLinks(lib.getName())));
				author.setText(Html.fromHtml(toHtmlLinks("by " + lib.getAuthorList((APDE) getApplicationContext()))));
				desc.setText(Html.fromHtml(toHtmlLinks(lib.getSentence((APDE) getApplicationContext()))));
				
				title.setMovementMethod(LinkMovementMethod.getInstance());
				author.setMovementMethod(LinkMovementMethod.getInstance());
				desc.setMovementMethod(LinkMovementMethod.getInstance());
				
				ImageButton button = (ImageButton) container.findViewById(R.id.library_manager_list_item_actions);
				
				button.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View button) {
						//Create a PopupMenu anchored to the "more actions" button
						//This is a custom implementation, designed to support API level 10+ (Android's PopupMenu is 11+)
						PopupMenu popup = new PopupMenu(getActivityContext(), button);
						
						//Populate the actions
						MenuInflater inflater = getMenuInflater();
						inflater.inflate(R.menu.library_manager_actions, popup.getMenu());
						
						//Detect presses
						popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
							@Override
							public boolean onMenuItemClick(MenuItem item) {
								switch(item.getItemId()) {
								case R.id.menu_contrib_uninstall:
									launchUninstallDialog(lib);
									return true;
								}
								
								return false;
							}
						});
						popup.show();
					}
				});
			}
			
			return view;
		}
	}
	
	private LibraryManagerActivity getActivityContext() {
		return this;
	}
	
	//This is straight from the Processing source - the class is default access, so we can't get to it from here
	/**
	 * This has a [link](http://example.com/) in [it](http://example.org/).
	 *
	 * Becomes...
	 *
	 * This has a <a href="http://example.com/">link</a> in <a href="http://example.org/">it</a>.
	 */
	public static String toHtmlLinks(String stringIn) {
		Pattern p = Pattern.compile("\\[(.*?)\\]\\((.*?)\\)");
		Matcher m = p.matcher(stringIn);
		
		StringBuilder sb = new StringBuilder();
		
		int start = 0;
		while(m.find(start)) {
			sb.append(stringIn.substring(start, m.start()));
			
			String text = m.group(1);
			String url = m.group(2);
			
			sb.append("<a href=\"");
			sb.append(url);
			sb.append("\">");
			sb.append(text);
			sb.append("</a>");
			
			start = m.end();
		}
		
		sb.append(stringIn.substring(start));
		return sb.toString();
	}
	
	private void launchUninstallDialog(final Library lib) {
		//Check to make sure that the user really wants to uninstall the library
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getResources().getString(R.string.uninstall_warning_title) + " " + lib.getName());
		builder.setMessage(lib.getName() + " " + getResources().getString(R.string.uninstall_warning_message));
		
		builder.setPositiveButton(R.string.uninstall_warning_title, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				uninstallLibrary(lib);
			}
		});
		
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {}
		});
		
		builder.create().show();
	}
	
	private void alert(int titleResID, int messageResID) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(titleResID);
		builder.setMessage(messageResID);
		
		builder.setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				
			}
		});
		
		builder.create().show();
	}
	
	//Handle library updates during installation
	//This all needs to be static to avoid memory leaks
	private static class LibraryUpdateHandler extends Handler {
		protected CustomProgressDialog dialog;
		protected Resources res;
		protected LibraryManagerActivity context;
		
		@Override
		public void handleMessage(final Message msg) {
			if(msg.what == ContributionManager.LIBRARY_UPDATE) {
				context.runOnUiThread(new Runnable() {
					@SuppressLint("NewApi")
					public void run() {
						switch((Library.Status) msg.obj) {
						case COPYING:
							dialog.setProgressText(res.getString(R.string.copying) + "...");
							break;
						case EXTRACTING:
							dialog.setProgressText(res.getString(R.string.extracting) + "...");
							break;
						case DEXING:
							dialog.setProgressText(res.getString(R.string.dexing) + "...");
							break;
						case INSTALLED:
							dialog.setProgress(2);
							break;
						}
					}
				});
			}
			
			super.handleMessage(msg);
		}
	};
	
	//Make sure to initialize it with the UI thread...
	private static LibraryUpdateHandler libraryUpdateHandler = new LibraryUpdateHandler();
	
	@SuppressLint("NewApi")
	private void addZipLibrary(final File libraryZip) {
		//Let's not do too many things at once...
		if(working)
			return;
		
		final APDE context = (APDE) getApplicationContext();
		
		String libraryName = ContributionManager.detectLibraryName(libraryZip);
		final Library library = new Library(libraryName);
		
		//Initialize the progress dialog
		final CustomProgressDialog dialog = new CustomProgressDialog(this, View.GONE, View.GONE);
		
		//Set up the handler
		libraryUpdateHandler.dialog = dialog;
		libraryUpdateHandler.res = getResources();
		libraryUpdateHandler.context = this;
		
		//Install the archived / compressed library in a new thread
		final Thread installThread = new Thread(new Runnable() {
			@Override
			public void run() {
				working = true;
				boolean success = ContributionManager.installZipLibrary(library, libraryZip, libraryUpdateHandler, context);
				working = false;
				
				if (!success) {
					runOnUiThread(new Runnable() {
						public void run() {
							alert(R.string.library_install_failed_title, R.string.library_install_failed_message);
						}
					});
				}
				
				findViewById(R.id.library_manager_list).post(new Runnable() {
					public void run() {
						context.rebuildLibraryList();
						refreshLibraryList();
						findViewById(R.id.library_manager_list).invalidate();
						
						dialog.dismiss();
					}
				});
			}
		});
		
		//Set up the progress dialog TODO It would be cool if this was a real progress bar, displaying percentage complete - 
		//but we can't get enough out of DEX to pull this off (and the dexer takes almost all of the time...)
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setTitle(getResources().getString(R.string.library_install_dialog_title) + " " + libraryName);
		dialog.setCanceledOnTouchOutside(false);
		dialog.setCancelable(false);
		//Or... we won't do this. TODO Can we make this work?
//		//Make this look like an indeterminate progress bar
//		dialog.setProgressDrawable(getResources().getDrawable(R.drawable.progress_indeterminate_horizontal_holo));
		
		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		
		dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialogInterface) {
				//The user cancelled... hopefully this won't cause problems
				installThread.interrupt();
				working = false;
				
				//Undo any progress thus far
				uninstallLibrary(library);
			}
		});
		
		//Let's get things going
		installThread.start();
		dialog.show();
	}
	
	private void uninstallLibrary(final Library library) {
		//Haha... we call it uninstall, but we're just deleting...
		
		final APDE context = (APDE) getApplicationContext();
		
		//Initialize the progress dialog
		final CustomProgressDialog dialog = new CustomProgressDialog(this, View.GONE, View.GONE);
		
		final Thread uninstallThread = new Thread(new Runnable() {
			public void run() {
				working = true;
				ContributionManager.uninstallLibrary(library, context);
				working = false;
				
				findViewById(R.id.library_manager_list).post(new Runnable() {
					public void run() {
						context.rebuildLibraryList();
						refreshLibraryList();
						findViewById(R.id.library_manager_list).invalidate();
						
						dialog.dismiss();
					}
				});
			}
		});
		
		//Set up the progress dialog TODO It would be cool if this was a real progress bar, displaying percentage complete - 
		//but we can't get enough out of DEX to pull this off (and the dexer takes almost all of the time...)
		dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		dialog.setIndeterminate(true);
		dialog.setTitle(getResources().getString(R.string.library_uninstall_dialog_title) + " " + library.getName());
		dialog.setCanceledOnTouchOutside(false);
		dialog.setCancelable(false);
		
		dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getResources().getString(R.string.run_in_background), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		
		//Let's get things going
		uninstallThread.start();
		dialog.show();
	}
}