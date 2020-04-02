package com.calsignlabs.apde.tool;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.ComponentTarget;
import com.calsignlabs.apde.build.dag.BuildContext;
import com.calsignlabs.apde.support.FileSelection;

import org.spongycastle.asn1.x509.X509Name;
import org.spongycastle.jce.X509Principal;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.x509.X509V3CertificateGenerator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

import javax.security.auth.x500.X500Principal;

import kellinwood.security.zipsigner.optional.JksKeyStore;
import kellinwood.security.zipsigner.optional.LoadKeystoreException;

/**
 * Exports the current sketch as an signed, installable APK.
 */
@SuppressWarnings("deprecation")
public class ExportSignedPackage implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ExportSignedPackage";
	
	public static final int REQUEST_KEYSTORE_FILE = 6;
	
	private static APDE context;
	
	private Build builder;
	private boolean exporting;
	
	private KeyStore keystore;
	private X509Certificate certificate;
	
	private AutoCompleteTextView keystoreFile;
	private ArrayAdapter<String> keystoreAdapter;
	private EditText keystorePassword;
	private ImageButton keystoreFileSelect;
	private ImageButton keystoreNew;
	
	private Spinner alias;
	private ArrayAdapter<String> aliasAdapter;
	private EditText aliasPassword;
	private ImageButton aliasCertificateInfo;
	private ImageButton aliasNew;
	
	private EditText createKeystoreFile;
	private EditText createKeystorePassword;
	private EditText createKeystorePasswordConfirm;
	private ImageButton createKeystoreFileSelect;
	
	private EditText createAliasAlias;
	private EditText createAliasPassword;
	private EditText createAliasPasswordConfirm;
	private EditText createAliasValidity;
	
	private TextView exportMessage;
	private TextView keystoreMessage;
	private TextView keyMessage;
	
	private Spinner componentTarget;
	private ArrayAdapter<String> componentTargetAdapter;
	
	private static class TicketVendor {
		private int newestTicket, lastTicket;
		
		public TicketVendor() {
			newestTicket = 0;
			lastTicket = 0;
		}
		
		public int requestTicket() {
			return ++ newestTicket;
		}
		
		public boolean isNewestTicket(int ticket) {
			// Technically this is succeptible to overflow but that shouldn't matter in practice
			if (ticket > lastTicket) {
				lastTicket = ticket;
				return true;
			} else {
				return false;
			}
		}
	}
	
	private TicketVendor keystoreFileValidation;
	private TicketVendor keystorePasswordValidation;
	private TicketVendor aliasPasswordValidation;
	
	@Override
	public void init(APDE context) {
		ExportSignedPackage.context = context;
		
		exporting = false;
		
		keystoreFileValidation = new TicketVendor();
		keystorePasswordValidation = new TicketVendor();
		aliasPasswordValidation = new TicketVendor();
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.export_signed_package);
	}
	
	@Override
	public void run() {
		switch(context.getSketchLocationType()) {
    	case EXAMPLE:
    	case LIBRARY_EXAMPLE:
    		break;
    	case SKETCHBOOK:
    	case EXTERNAL:
		case TEMPORARY:
    		context.getEditor().saveSketch();
    		break;
    	}
		
		//Don't try to export if we're already exporting...
		if (exporting) {
			return;
		}
		
		promptSigningKey();
	}
	
	protected Uri getKeystoreFileUri() {
		return FileSelection.uriStringOrPathToUri(keystoreFile.getText().toString());
	}
	
	protected Uri getCreateKeystoreFileUri() {
		return FileSelection.uriStringOrPathToUri(createKeystoreFile.getText().toString());
	}
	
	@SuppressLint("InlinedApi")
	protected void promptSigningKey() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
		builder.setTitle(context.getResources().getText(R.string.export_signed_package));
		
		final ScrollView layout;

		layout = (ScrollView) View.inflate(new ContextThemeWrapper(context, R.style.Theme_AppCompat_Dialog), R.layout.export_signed_package, null);
		
		keystoreFile = layout.findViewById(R.id.keystore_file);
		keystorePassword = layout.findViewById(R.id.keystore_password);
		keystoreFileSelect = layout.findViewById(R.id.keystore_file_select);
		keystoreNew = layout.findViewById(R.id.keystore_new);
		
		alias = layout.findViewById(R.id.alias);
		aliasPassword = layout.findViewById(R.id.alias_password);
		aliasCertificateInfo = layout.findViewById(R.id.alias_certificate_info);
		aliasNew = layout.findViewById(R.id.alias_new);
		
		componentTarget = layout.findViewById(R.id.component_target);
		
		exportMessage = layout.findViewById(R.id.export_signed_package_message);
		
		builder.setView(layout);
		
		builder.setNegativeButton(R.string.cancel, (dialog, which) -> {});
		
		builder.setPositiveButton(R.string.export_signed_package_export, (dialog, which) -> releaseBuild());
		
		//Info button - this is mostly warnings and disclaimers
		builder.setNeutralButton(R.string.export_signed_package_long_info_button, null);
		
		final AlertDialog dialog = builder.create();
		
		dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
			AlertDialog.Builder infoBuilder = new AlertDialog.Builder(context.getEditor());
			
			infoBuilder.setTitle(R.string.export_signed_package_long_info_title);
			infoBuilder.setMessage(R.string.export_signed_package_long_info_message);
			
			infoBuilder.setPositiveButton(R.string.ok, (dialog12, which) -> {
			});
			
			AlertDialog infoDialog = infoBuilder.create();
			
			infoDialog.show();
			infoDialog.getWindow().getAttributes();
			
			TextView messageTextView = infoDialog.findViewById(android.R.id.message);
			messageTextView.setTextSize(12);
			
			messageTextView.setTextIsSelectable(true);
			
			//Don't dismiss the dialog!!
		}));
		
		dialog.show();
		
		final Button exportButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		
		exportButton.setEnabled(false);
		
		keystorePassword.setEnabled(false);
		
		alias.setEnabled(false);
		aliasPassword.setEnabled(false);
		aliasCertificateInfo.setEnabled(false);
		aliasNew.setEnabled(false);
		
		keystoreAdapter = new ArrayAdapter<String>(context, R.layout.alias_spinner_dropdown_item);
		keystoreFile.setAdapter(keystoreAdapter);
		loadRecentKeystores();
		
		keystorePassword.requestLayout();
		keystorePassword.post(() -> keystoreFile.setDropDownWidth(keystorePassword.getWidth()));
		
		ArrayList<String> aliasList = new ArrayList<String>();
		aliasList.add(context.getResources().getString(R.string.export_signed_package_no_aliases));
		
		//The alias spinner is empty until the user selects a keystore
		aliasAdapter = new ArrayAdapter<String>(context, R.layout.spinner_item, aliasList);
		aliasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		alias.setAdapter(aliasAdapter);
		
		String[] componentTargetList = {
				context.getResources().getString(ComponentTarget.APP.getNameId()),
				context.getResources().getString(ComponentTarget.WALLPAPER.getNameId()),
				context.getResources().getString(ComponentTarget.WATCHFACE.getNameId()),
				context.getResources().getString(ComponentTarget.VR.getNameId())
		};
		
		componentTargetAdapter = new ArrayAdapter<String>(context, R.layout.spinner_item, componentTargetList);
		componentTargetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		componentTarget.setAdapter(componentTargetAdapter);
		
		int selectedTarget = context.getEditor().getComponentTarget().serialize();
		if (selectedTarget == ComponentTarget.PREVIEW.serialize()) {
			// Preview -> App
			selectedTarget = ComponentTarget.APP.serialize();
		}
		// Sanity check
		componentTarget.setSelection(Math.min(Math.max(selectedTarget, 0), 3), false);
		
		keystoreFileSelect.setOnClickListener(view -> {
			Intent intent = FileSelection.createFileSelectorIntent(false, null);
			context.getEditor().selectFile(intent, REQUEST_KEYSTORE_FILE, (requestCode, resultCode, data) -> {
				if (resultCode == Activity.RESULT_OK) {
					Uri uri = FileSelection.getSelectedUri(data);
					if (uri != null) {
						keystoreFile.setText(uri.toString());
					}
				}
			});
		});
		
		keystoreNew.setOnClickListener(view -> promptCreateKeystore());
		
		keystoreFile.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				keystorePassword.setText("");
				
				alias.setEnabled(false);
				aliasAdapter.clear();
				aliasAdapter.add(context.getResources().getString(R.string.export_signed_package_no_aliases));
				aliasPassword.setEnabled(false);
				aliasPassword.setText("");
				aliasCertificateInfo.setEnabled(false);
				aliasNew.setEnabled(false);
				
				int ticket = keystoreFileValidation.requestTicket();
				
				// Do validation in background so that the UI doesn't freeze
				(new Thread(() -> {
					ValidationResult result = validateKeystoreFile();
					
					context.getEditor().runOnUiThread(() -> {
						if (keystoreFileValidation.isNewestTicket(ticket)) {
							
							if (result.resultCode() == 0) {
								keystorePassword.setEnabled(true);
								// Hide the dropdown
								keystoreFile.dismissDropDown();
								messageView(exportMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_password);
							} else {
								keystorePassword.setEnabled(false);
								messageView(exportMessage, result);
							}
						}
					});
				})).start();
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
		
		keystorePassword.addTextChangedListener(new TextWatcher() {
			@SuppressWarnings("unchecked")
			@Override
			public void afterTextChanged(Editable arg) {
				aliasNew.setEnabled(false);
				aliasPassword.setEnabled(false);
				aliasCertificateInfo.setEnabled(false);
				
				int ticket = keystorePasswordValidation.requestTicket();
				
				// Run validation in background so that we don't freeze the UI thread
				if (keystorePassword.getText().length() > 0) {
					(new Thread(() -> {
						ValidationResult result = loadKeystore(keystoreFile.getText().toString(),
								getKeystoreFileUri(), keystorePassword.getText().toString().toCharArray());
						
						context.getEditor().runOnUiThread(() -> {
							if (keystorePasswordValidation.isNewestTicket(ticket)) {
								if (result.resultCode() == 0) {
									loadAliases((ArrayList<String>) result.extra());
									
									if (alias.isEnabled()) {
										messageView(exportMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_key_password);
										aliasPassword.setEnabled(true);
										aliasCertificateInfo.setEnabled(true);
									} else {
										messageView(exportMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_create_key);
									}
									
									aliasNew.setEnabled(true);
								} else {
									messageView(exportMessage, result);
									loadAliases(new ArrayList<String>());
								}
							}
						});
					})).start();
				} else {
					if (keystorePasswordValidation.isNewestTicket(ticket)) {
						messageView(exportMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_password);
					}
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
		
		alias.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				aliasPassword.setText("");
				
				loadCertificate(keystoreFile.getText().toString(), getKeystoreFileUri(),
						keystorePassword.getText().toString().toCharArray(),
						aliasAdapter.getItem(alias.getSelectedItemPosition()));
			}
			
			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});
		
		aliasCertificateInfo.setOnClickListener(v -> {
			loadCertificate(keystoreFile.getText().toString(), getKeystoreFileUri(),
					keystorePassword.getText().toString().toCharArray(),
					aliasAdapter.getItem(alias.getSelectedItemPosition()));
			
			AlertDialog.Builder infoBuilder = new AlertDialog.Builder(context.getEditor());
			
			infoBuilder.setTitle(R.string.export_signed_package_certificate_info);
			
			TableLayout infoLayout;
			
			infoLayout = (TableLayout) View.inflate(new ContextThemeWrapper(context, R.style.Theme_AppCompat_Dialog), R.layout.certificate_info, null);
			
			((TextView) infoLayout.findViewById(R.id.alias_name)).setText((String) alias.getSelectedItem());
			((TextView) infoLayout.findViewById(R.id.certificate_expiration)).setText(new SimpleDateFormat(context.getResources().getString(R.string.export_signed_package_date_format), Locale.US).format(certificate.getNotAfter()));
			
			Principal principal = certificate.getSubjectDN();
			
			if (principal instanceof X500Principal) {
				//This seems to work with keystores generated with the desktop keytool
				
				X500Principal x500Principal = (X500Principal) principal;
				X509Principal x509 = new X509Principal(x500Principal.getName()); //Because X509Principal parses the String for us
				
				((TextView) infoLayout.findViewById(R.id.alias_certificate_name)).setText((String) x509.getValues(X509Name.CN).get(0));
				((TextView) infoLayout.findViewById(R.id.alias_certificate_organizational_unit)).setText((String) x509.getValues(X509Name.OU).get(0));
				((TextView) infoLayout.findViewById(R.id.alias_certificate_organization)).setText((String) x509.getValues(X509Name.O).get(0));
				((TextView) infoLayout.findViewById(R.id.alias_certificate_city)).setText((String) x509.getValues(X509Name.L).get(0));
				((TextView) infoLayout.findViewById(R.id.alias_certificate_state)).setText((String) x509.getValues(X509Name.ST).get(0));
				((TextView) infoLayout.findViewById(R.id.alias_certificate_country)).setText((String) x509.getValues(X509Name.C).get(0));
			} else {
				//This seems to work with keystores generated by APDE... does that make that our generator is broken?
				
				//Yuck, reflection... but Android hides the BouncyCastle APIs that our certificate forces us to use
				try {
					Class<?> x509Principal = Class.forName("com.android.org.bouncycastle.jce.X509Principal");
					Class<?> x509Name = Class.forName("com.android.org.bouncycastle.asn1.x509.X509Name");
					Class<?> ans1Ident = Class.forName("com.android.org.bouncycastle.asn1.ASN1ObjectIdentifier");
					Method getValues = x509Principal.getMethod("getValues", ans1Ident);
					
					if (x509Principal.isInstance(principal)) {
						((TextView) infoLayout.findViewById(R.id.alias_certificate_name)).setText((String) ((Vector) getValues.invoke(x509Principal.cast(principal), x509Name.getField("CN").get(null))).get(0));
						((TextView) infoLayout.findViewById(R.id.alias_certificate_organizational_unit)).setText((String) ((Vector) getValues.invoke(x509Principal.cast(principal), x509Name.getField("OU").get(null))).get(0));
						((TextView) infoLayout.findViewById(R.id.alias_certificate_organization)).setText((String) ((Vector) getValues.invoke(x509Principal.cast(principal), x509Name.getField("O").get(null))).get(0));
						((TextView) infoLayout.findViewById(R.id.alias_certificate_city)).setText((String) ((Vector) getValues.invoke(x509Principal.cast(principal), x509Name.getField("L").get(null))).get(0));
						((TextView) infoLayout.findViewById(R.id.alias_certificate_state)).setText((String) ((Vector) getValues.invoke(x509Principal.cast(principal), x509Name.getField("ST").get(null))).get(0));
						((TextView) infoLayout.findViewById(R.id.alias_certificate_country)).setText((String) ((Vector) getValues.invoke(x509Principal.cast(principal), x509Name.getField("C").get(null))).get(0));
					}
				} catch(ClassNotFoundException | NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchFieldException e) {
					e.printStackTrace();
				} catch (Exception e) {
					//...
					System.err.println(context.getResources().getString(R.string.export_signed_package_certificate_read_failed));
					e.printStackTrace();
				}
			}
			
			infoBuilder.setView(infoLayout);
			
			infoBuilder.setPositiveButton(R.string.ok, (dialog1, which) -> {
			});
			
			infoBuilder.create().show();
		});
		
		aliasNew.setOnClickListener(view -> promptCreateKey());
		
		aliasPassword.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				int ticket = aliasPasswordValidation.requestTicket();
				
				if (aliasPassword.length() > 0) {
					(new Thread(() -> {
						ValidationResult result = loadKey(keystoreFile.getText().toString(),
								getKeystoreFileUri(),
								keystorePassword.getText().toString().toCharArray(),
								aliasAdapter.getItem(alias.getSelectedItemPosition()),
								aliasPassword.getText().toString().toCharArray());
						
						context.getEditor().runOnUiThread(() -> {
							if (aliasPasswordValidation.isNewestTicket(ticket)) {
								if (result.resultCode() == 0) {
									exportButton.setEnabled(true);
									
									messageView(exportMessage, ValidationResult.MessageSeverity.INFO,
											String.format(Locale.US, context.getResources().getString(R.string.export_signed_package_info_export_ready),
													context.getSketchName(),
													new SimpleDateFormat(context.getResources().getString(R.string.export_signed_package_date_format), Locale.US).format(certificate.getNotAfter())));
								} else {
									exportButton.setEnabled(false);
									
									messageView(exportMessage, result);
								}
							}
						});
					})).start();
				} else {
					if (aliasPasswordValidation.isNewestTicket(ticket)) {
						exportButton.setEnabled(false);
						
						if (alias.isEnabled()) {
							messageView(exportMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_key_password);
						}
					}
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		});
		
		messageView(exportMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_file);
		
		if (keystoreAdapter.getCount() > 0) {
			//Automatically select the first keystore in the list - if the user wants to change it, they can delete the text...
			keystoreFile.setText(keystoreAdapter.getItem(0));
			keystoreFile.dismissDropDown();
		}
	}
	
	@SuppressLint("InlinedApi")
	protected void promptCreateKeystore() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
		builder.setTitle(context.getResources().getText(R.string.export_signed_package_keystore_new));
		
		final ScrollView layout;
		
		layout = (ScrollView) View.inflate(new ContextThemeWrapper(context, R.style.Theme_AppCompat_Dialog), R.layout.create_keystore, null);
		
		createKeystoreFile = layout.findViewById(R.id.create_keystore_file);
		createKeystorePassword = layout.findViewById(R.id.create_keystore_password);
		createKeystorePasswordConfirm = layout.findViewById(R.id.create_keystore_password_confirm);
		createKeystoreFileSelect = layout.findViewById(R.id.create_keystore_file_select);
		
		keystoreMessage = layout.findViewById(R.id.create_keystore_message);
		
		builder.setView(layout);
		
		builder.setNegativeButton(R.string.cancel, (dialog, which) -> {});
		
		builder.setPositiveButton(R.string.create, (dialog, which) -> {
			writeEmptyKeystore(createKeystoreFile.getText().toString(),
					getCreateKeystoreFileUri(), createKeystorePassword.getText().toString().toCharArray());
			
			keystoreFile.setText(createKeystoreFile.getText().toString());
			keystorePassword.setText(createKeystorePassword.getText().toString());
		});
		
		final AlertDialog dialog = builder.create();
		dialog.show();
		
		final Button createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		
		createButton.setEnabled(false);
		
		createKeystoreFileSelect.setOnClickListener(view -> {
			Intent intent = FileSelection.createFileCreatorIntent("keystore.jks");
			context.getEditor().selectFile(intent, REQUEST_KEYSTORE_FILE, (requestCode, resultCode, data) -> {
				if (resultCode == Activity.RESULT_OK) {
					Uri uri = FileSelection.getSelectedUri(data);
					if (uri != null) {
						createKeystoreFile.setText(uri.toString());
					}
				}
			});
		});
		
		TextWatcher createKeystoreValidator = new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				//This is ugly... so what?
				if (createKeystoreFile.getText().length() > 0) {
					if (createKeystorePassword.getText().length() > 0 && createKeystorePasswordConfirm.getText().length() > 0) {
						if (createKeystorePasswordConfirm.getText().toString().equals(createKeystorePassword.getText().toString())) {
							Uri keystoreUri = getKeystoreFileUri();
							
							if (createKeystorePassword.getText().length() < 8) {
								//Trying to get users to use stronger passwords... is this a good idea?
								//This won't stop them, it's just a warning...
								//Is an 8-character password long enough?
								//...
								//It comes down to: Something is better than nothing
								
								messageView(keystoreMessage, ValidationResult.MessageSeverity.WARNING, R.string.export_signed_package_warning_short_password);
							} else {
								messageView(keystoreMessage, ValidationResult.MessageSeverity.INFO,
										String.format(Locale.US, context.getResources().getString(R.string.export_signed_package_info_create_keystore_ready),
												context.getResources().getString(isBksKeystore(keystoreUri) ? R.string.export_signed_package_keystore_type_bks : R.string.export_signed_package_keystore_type_jks)));
							}
							
							createButton.setEnabled(true);
							
							return;
						} else {
							messageView(keystoreMessage, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_passwords_disagree);
						}
					} else {
						messageView(keystoreMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_password);
					}
				} else {
					messageView(keystoreMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_file);
				}
				
				createButton.setEnabled(false);
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		};
		
		createKeystoreFile.addTextChangedListener(createKeystoreValidator);
		createKeystorePassword.addTextChangedListener(createKeystoreValidator);
		createKeystorePasswordConfirm.addTextChangedListener(createKeystoreValidator);
		
		messageView(keystoreMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_file);
	}
	
	@SuppressLint("InlinedApi")
	protected void promptCreateKey() {
		AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
		builder.setTitle(context.getResources().getText(R.string.export_signed_package_key_alias_new));
		
		final ScrollView layout;
		
		layout = (ScrollView) View.inflate(new ContextThemeWrapper(context, R.style.Theme_AppCompat_Dialog), R.layout.create_alias, null);
		
		createAliasAlias = layout.findViewById(R.id.create_alias_alias);
		createAliasPassword = layout.findViewById(R.id.create_alias_password);
		createAliasPasswordConfirm = layout.findViewById(R.id.create_alias_password_confirm);
		
		keyMessage = layout.findViewById(R.id.create_key_message);
		
		createAliasValidity = layout.findViewById(R.id.create_alias_validity);
		
		final EditText certificateName = layout.findViewById(R.id.create_alias_certificate_name);
		final EditText certificateOrganizationalUnit = layout.findViewById(R.id.create_alias_certificate_organizational_unit);
		final EditText certificateOrganization = layout.findViewById(R.id.create_alias_certificate_organization);
		final EditText certificateCity = layout.findViewById(R.id.create_alias_certificate_city);
		final EditText certificateState = layout.findViewById(R.id.create_alias_certificate_state);
		final EditText certificateCountry = layout.findViewById(R.id.create_alias_certificate_country);
		
		builder.setView(layout);
		
		builder.setNegativeButton(R.string.cancel, (dialog, which) -> {});
		
		builder.setPositiveButton(R.string.create, (dialog, which) -> {
			aliasCertificateInfo.setEnabled(true);
			aliasPassword.setEnabled(true);
			
			writeKey(keystoreFile.getText().toString(), getKeystoreFileUri(), keystorePassword.getText().toString().toCharArray(),
					createAliasAlias.getText().toString(), createAliasPassword.getText().toString().toCharArray(), Integer.parseInt(createAliasValidity.getText().toString()),
					certificateName.getText().toString(), certificateOrganizationalUnit.getText().toString(), certificateOrganization.getText().toString(),
					certificateCity.getText().toString(), certificateState.getText().toString(), certificateCountry.getText().toString());
			
			alias.setSelection(((ArrayAdapter<String>) alias.getAdapter()).getPosition(createAliasAlias.getText().toString()));
			aliasPassword.setText(new String(createAliasPassword.getText().toString()));
		});
		
		final AlertDialog dialog = builder.create();
		dialog.show();
		
		final Button createButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		
		createButton.setEnabled(false);
		
		TextWatcher createKeyValidator = new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				if (createAliasAlias.getText().length() > 0) {
					if (createAliasPassword.getText().length() > 0 && createAliasPasswordConfirm.getText().length() > 0) {
						if (createAliasValidity.getText().length() > 0) {
							if (createAliasPassword.getText().toString().equals(createAliasPasswordConfirm.getText().toString())) {
								boolean containsAlias = false;
								
								try {
									containsAlias = keystore.containsAlias(createAliasAlias.getText().toString());
								} catch (KeyStoreException e) {
									//Uh-oh... this shouldn't happen
									
									messageView(keyMessage, new ValidationResult(13, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_unexpected));
									
									createButton.setEnabled(false);
									
									e.printStackTrace();
									
									return;
								}
								
								if (containsAlias) {
									messageView(keyMessage, ValidationResult.MessageSeverity.WARNING, R.string.export_signed_package_warning_key_exists);
								} else if (Integer.parseInt(createAliasValidity.getText().toString()) < 25) {
									messageView(keyMessage, ValidationResult.MessageSeverity.WARNING, R.string.export_signed_package_warning_certificate_validity_short);
								} else if (createAliasPassword.getText().length() < 8) {
									//Trying to get users to use stronger passwords... is this a good idea?
									//This won't stop them, it's just a warning...
									//Is an 8-character password long enough?
									//...
									//It comes down to: Something is better than nothing
									
									messageView(keyMessage, ValidationResult.MessageSeverity.WARNING, R.string.export_signed_package_warning_short_password);
								} else if (certificateName.getText().length() == 0
										|| certificateOrganizationalUnit.getText().length() == 0
										|| certificateOrganization.getText().length() == 0
										|| certificateCity.getText().length() == 0
										|| certificateState.getText().length() == 0
										|| certificateCountry.getText().length() == 0) {
									
									messageView(keyMessage, ValidationResult.MessageSeverity.WARNING, R.string.export_signed_package_warning_certificate_empty);
								} else {
									messageView(keyMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_create_key_ready);
								}
								
								createButton.setEnabled(true);
								
								return;
							} else {
								messageView(keyMessage, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_passwords_disagree);
							}
						} else {
							messageView(keyMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_certificate_validity);
						}
					} else {
						messageView(keyMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_alias_password);
					}
				} else {
					messageView(keyMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_alias);
				}
				
				createButton.setEnabled(false);
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
		};
		
		createAliasAlias.addTextChangedListener(createKeyValidator);
		createAliasPassword.addTextChangedListener(createKeyValidator);
		createAliasPasswordConfirm.addTextChangedListener(createKeyValidator);
		
		createAliasValidity.addTextChangedListener(createKeyValidator);
		
		certificateName.addTextChangedListener(createKeyValidator);
		certificateOrganizationalUnit.addTextChangedListener(createKeyValidator);
		certificateOrganization.addTextChangedListener(createKeyValidator);
		certificateCity.addTextChangedListener(createKeyValidator);
		certificateState.addTextChangedListener(createKeyValidator);
		certificateCountry.addTextChangedListener(createKeyValidator);
		
		messageView(keyMessage, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_alias);
	}
	
	protected ValidationResult validateKeystoreFile() {
		Uri keystoreUri = getKeystoreFileUri();
		
		ParcelFileDescriptor fd = FileSelection.openUri(context, keystoreUri, FileSelection.Mode.READ, true);
		
		try {
			if (fd != null) {
				return new ValidationResult(0);
			} else {
				if (keystoreFile.getText().length() > 0) {
					return new ValidationResult(1, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_keystore_file_nonexistant);
				} else {
					return new ValidationResult(2, ValidationResult.MessageSeverity.INFO, R.string.export_signed_package_info_enter_keystore_file);
				}
			}
		} finally {
			FileSelection.closeFd(fd);
		}
	}
	
	protected ValidationResult loadKeystore(String uriPath, Uri uri, char[] password) {
		ValidationResult result = new ValidationResult(13, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_unexpected);
		
		ParcelFileDescriptor fd = FileSelection.openUri(context, uri, FileSelection.Mode.READ, true);
		InputStream in = null;
		
		if (fd == null) {
			return result;
		}
		
		//If no password is provided, it still gives us the list of aliases... but we can't use it
		//Let's just side-step this altogether
		if (password.length > 0) {
			try {
				in = FileSelection.fdIn(fd);
				
				keystore = new JksKeyStore();
				keystore.load(in, password);
				
				List<String> aliases = new ArrayList<String>();
				aliases = Collections.list(keystore.aliases());
				
				putRecentKeystore(uriPath);
				loadRecentKeystores();
				
				result = new ValidationResult(0, aliases);
			} catch (LoadKeystoreException e) {
				//We have a JKS keystore, but it's either corrupted or we have a bad password
				result = new ValidationResult(2, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_keystore_not_recoverable);
			} catch (FileNotFoundException e) {
				result = new ValidationResult(1, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_keystore_file_nonexistant);
			} catch (NoSuchAlgorithmException | CertificateException e) {
				e.printStackTrace();
			} catch (IOException e) {
				//Okay, let's try BKS...
				
				try {
					in = FileSelection.fdIn(fd);
					
					keystore = KeyStore.getInstance("bks");
					keystore.load(in, password);
					
					List<String> aliases = new ArrayList<String>();
					aliases = Collections.list(keystore.aliases());
					
					putRecentKeystore(uriPath);
					loadRecentKeystores();
					
					result = new ValidationResult(0, aliases);
				} catch (FileNotFoundException x) {
					result = new ValidationResult(1, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_keystore_file_nonexistant);
				} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException x) {
					e.printStackTrace();
				} catch (IOException x) {
					//So... it's neither JKS nor BKS...
					
					result = new ValidationResult(2, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_keystore_not_recoverable);
				} catch (OutOfMemoryError x) {
					//This seems to happen when trying to load files with names like ".nomedia"
					//Is this a bug in BouncyCastle? Is it because files starting with dots are hidden? Or is there something wrong with this particular ".nomedia" file?
					e.printStackTrace();
				} finally {
					try {
						if (in != null) {
							in.close();
						}
					} catch (IOException x) {
						e.printStackTrace();
					}
				}
			} catch (OutOfMemoryError e) {
				//This seems to happen when trying to load files with names like ".nomedia"
				//Is this a bug in BouncyCastle? Is it because files starting with dots are hidden? Or is there something wrong with this particular ".nomedia" file?
				e.printStackTrace();
			} catch (KeyStoreException e) {
				e.printStackTrace();
			} finally {
				try {
					if (in != null) {
						in.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return result;
	}
	
	@SuppressLint("NewApi")
	protected void loadRecentKeystores() {
		keystoreAdapter.clear();
		
		ArrayList<String> keystores = getRecentKeystores();
		
		if(keystores.size() > 0) {
			keystoreAdapter.addAll(keystores);
		}
	}
	
	@SuppressLint("NewApi")
	protected void loadAliases(List<String> aliases) {
		aliasAdapter.clear();
		
		if (aliases.size() > 0) {
			// I think this is better performance than doing them individually
			aliasAdapter.addAll(aliases);
			
			alias.setEnabled(true);
		} else {
			aliasAdapter.add(context.getResources().getString(R.string.export_signed_package_no_aliases));
			alias.setEnabled(false);
			aliasPassword.setText("");
		}
	}
	
	protected ValidationResult loadCertificate(String keystoreUriPath, Uri keystoreUri, char[] keystorePassword, String alias) {
		Security.addProvider(new BouncyCastleProvider());
		
		ValidationResult result = new ValidationResult(13, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_unexpected);
		
		ValidationResult keystoreResult = loadKeystore(keystoreUriPath, keystoreUri, keystorePassword);
		
		if (keystoreResult.resultCode() != 0) {
			result = keystoreResult;
		} else {
			try {
				if (keystore.containsAlias(alias)) {
					certificate = (X509Certificate) keystore.getCertificate(alias);
					
					//We just need to make sure that we can load these - we don't actually have to use them...
					result = new ValidationResult(0);
				}
			} catch (KeyStoreException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	protected ValidationResult loadKey(String keystoreUriPath, Uri keystoreUri, char[] keystorePassword, String alias, char[] password) {
		ValidationResult result = new ValidationResult(13, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_unexpected);
		
		ValidationResult keystoreResult = loadKeystore(keystoreUriPath, keystoreUri, keystorePassword);
		
		if (keystoreResult.resultCode() != 0) {
			result = keystoreResult;
		} else {
			try {
				if (password.length > 0) {
					if (keystore.containsAlias(alias)) {
						certificate = (X509Certificate) keystore.getCertificate(alias);
						@SuppressWarnings("unused")
						PrivateKey key = (PrivateKey) keystore.getKey(alias, password);
						
						certificate.checkValidity();
						
						//We just need to make sure that we can load these - we don't actually have to use them...
						result = new ValidationResult(0);
					}
				}
			} catch (KeyStoreException e) {
				e.printStackTrace();
			} catch (UnrecoverableKeyException e) {
				result = new ValidationResult(3, ValidationResult.MessageSeverity.ERROR, R.string.export_signed_package_error_key_not_recoverable);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (CertificateExpiredException e) {
				result = new ValidationResult(4, ValidationResult.MessageSeverity.ERROR,
						String.format(Locale.US, context.getResources().getString(R.string.export_signed_package_error_certificate_expired),
								new SimpleDateFormat(context.getResources().getString(R.string.export_signed_package_date_format), Locale.US).format(certificate.getNotAfter())));
			} catch (CertificateNotYetValidException e) {
				result = new ValidationResult(5, ValidationResult.MessageSeverity.ERROR,
						String.format(Locale.US, context.getResources().getString(R.string.export_signed_package_error_certificate_not_yet_valid),
								new SimpleDateFormat(context.getResources().getString(R.string.export_signed_package_date_format), Locale.US).format(certificate.getNotBefore())));
			}
		}
		
		return result;
	}
	
	protected void writeEmptyKeystore(String uriPath, Uri uri, char[] password) {
		try {
			//Create a JKS keystore by default
			//If the extension is ".bks", then make it a BKS keystore
			if (isBksKeystore(uri)) {
				keystore = KeyStore.getInstance("bks");
			} else {
				keystore = new JksKeyStore();
			}
			
			keystore.load(null, password); //Initialize
			
			writeKeystore(uriPath, uri, password);
		} catch (KeyStoreException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean isBksKeystore(Uri uri) {
		String filename = FileSelection.uriToFilename(context, uri);
		return filename != null && filename.endsWith(".bks");
	}
	
	protected void writeKeystore(String uriPath, Uri uri, char[] password) {
		ParcelFileDescriptor fd = FileSelection.openUri(context, uri, FileSelection.Mode.READ_WRITE, true);
		if (fd == null) {
			System.err.println("Failed to write keystore to Uri: " + uri.toString());
			return;
		}
		
		OutputStream out = null;
		
		try {
			out = FileSelection.fdOut(fd);
			
			keystore.store(out, password);
			
			putRecentKeystore(uriPath);
			loadRecentKeystores();
		} catch (KeyStoreException e) {
			//Hmm...
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			//Shouldn't happen
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (CertificateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		FileSelection.closeFd(fd);
	}
	
	@SuppressWarnings("unchecked")
	protected void writeKey(String keystoreUriPath, Uri keystoreUri, char[] keystorePassword,
							String alias, char[] password, int validity, String name,
							String orgUnit, String org, String city, String state, String country) {
		try {
			Security.addProvider(new BouncyCastleProvider());
			
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
			keyGen.initialize(1024, random);
			KeyPair pair = keyGen.generateKeyPair();
			
			X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();
			
			X509Principal principal = new X509Principal("CN=" + formatDN(name) + ", OU=" + formatDN(orgUnit) + ", O=" + formatDN(org)
					+ ", L=" + formatDN(city) + ", ST=" + formatDN(state) + ", C=" + formatDN(country));
			
			int serial = new SecureRandom().nextInt();
			
			v3CertGen.setSerialNumber(BigInteger.valueOf(serial < 0 ? -1 * serial : serial));
			v3CertGen.setIssuerDN(principal);
			v3CertGen.setNotBefore(new Date(System.currentTimeMillis()));
			v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365 * validity))); //TODO Doesn't take leap days / years into account...
			v3CertGen.setSubjectDN(principal);
			v3CertGen.setPublicKey(pair.getPublic());
			v3CertGen.setSignatureAlgorithm("MD5WithRSAEncryption");
			
			X509Certificate pkCertificate = v3CertGen.generateX509Certificate(pair.getPrivate());
			
			keystore.setKeyEntry(alias, pair.getPrivate(), password, new Certificate[] {pkCertificate});
			
			//Write the new key to the keystore
			writeKeystore(keystoreUriPath, keystoreUri, keystorePassword);
			
			//Reload the keystore so that the new key will appear
			loadAliases((ArrayList<String>) loadKeystore(keystoreUriPath, keystoreUri, keystorePassword).extra());
		} catch (NoSuchAlgorithmException | KeyStoreException | InvalidKeyException | SecurityException | SignatureException e) {
			e.printStackTrace();
		}
	}
	
	private String formatDN(String value) {
		//DN specification reference:
		//http://www.ietf.org/rfc/rfc4514.txt
		
		//Other special characters (think other languages) may or may not crash everything...
		
		//Escape special characters...
		final char[] escapeChars = {'\\', '"', '#', '+', ',', ';', '=', '<', '>'}; //Note: "\" character must be escaped first...
		
		for (char escape : escapeChars) {
			value = value.replace(Character.toString(escape), "\\" + escape);
		}
		
		return value.length() > 0 ? value : context.getString(R.string.export_signed_package_certificate_field_empty_default);
	}
	
	public void putRecentKeystore(String path) {
		ArrayList<String> oldKeystores = getRecentKeystores();
		String[] keystores = new String[oldKeystores.size() + 1];
		
		//Add the new keystore
		keystores[0] = path;
		//Copy all of the old keystores over
		System.arraycopy(oldKeystores.toArray(), 0, keystores, 1, oldKeystores.size());
		
		//We should get a list with the newest keystores on top...
		
		String data = "";
		
		for (int i = 0; i < keystores.length; i ++) {
			data += keystores[i] + "\n";
		}
		
		PreferenceManager.getDefaultSharedPreferences(context).edit().putString("recentKeystores", data).apply();
	}
	
	public ArrayList<String> getRecentKeystores() {
		String data = PreferenceManager.getDefaultSharedPreferences(context).getString("recentKeystores", "");
		String[] keystoreLines = data.split("\n");
		
		ArrayList<String> keystores = new ArrayList<String>(keystoreLines.length);
		
		Set<String> seenKeystores = new HashSet<>();
		
		// 10 here is the number of keystores to keep in the recent list
		// TODO maybe make this a preference?
		for (int i = Math.min(keystoreLines.length - 1, 10); i >= 0; i --) {
			String keystorePath = keystoreLines[i];
			
			// Skip over bad data - this should only happen if the saved data is empty
			if (keystorePath.length() == 0) {
				continue;
			}
			
			// Avoid duplicates
			if (seenKeystores.contains(keystorePath)) {
				continue;
			} else {
				seenKeystores.add(keystorePath);
			}
			
			Uri keystoreUri = FileSelection.uriStringOrPathToUri(keystorePath);
			if (keystoreUri == null) {
				continue;
			}
			
			// Filter out bad keystores (perhaps deleted files?)
			ParcelFileDescriptor fd = FileSelection.openUri(context, keystoreUri, FileSelection.Mode.READ, true);
			if (fd == null) {
				continue;
			} else {
				FileSelection.closeFd(fd);
			}
			
			keystores.add(keystorePath);
		}
		
		// Reverse the list...
		Collections.reverse(keystores);
		
		return keystores;
	}
	
	public static final class ValidationResult {
		private int resultCode;
		private MessageSeverity severity;
		private String message;
		private Object extra;
		
		public ValidationResult(int resultCode) {
			this.resultCode = resultCode;
			this.severity = MessageSeverity.INFO;
			this.message = "";
			extra = null;
		}
		
		public ValidationResult(int resultCode, Object extra) {
			this.resultCode = resultCode;
			this.severity = MessageSeverity.INFO;
			this.message = "";
			this.extra = extra;
		}
		
		public ValidationResult(int resultCode, MessageSeverity severity, String message) {
			this.resultCode = resultCode;
			this.severity = severity;
			this.message = message;
			extra = null;
		}
		
		public ValidationResult(int resultCode, MessageSeverity severity, int messageId) {
			this.resultCode = resultCode;
			this.severity = severity;
			message = context.getResources().getString(messageId);
			extra = null;
		}
		
		public ValidationResult(int resultCode, MessageSeverity severity, String message, Object extra) {
			this.resultCode = resultCode;
			this.severity = severity;
			this.message = message;
			this.extra = extra;
		}
		
		public ValidationResult(int resultCode, MessageSeverity severity, int messageId, Object extra) {
			this.resultCode = resultCode;
			this.severity = severity;
			message = context.getResources().getString(messageId);
			this.extra = extra;
		}
		
		public int resultCode() {
			return resultCode;
		}
		
		public MessageSeverity severity() {
			return severity;
		}
		
		public String message() {
			return message;
		}
		
		public Object extra() {
			return extra;
		}
		
		public static enum MessageSeverity {
			INFO, WARNING, ERROR
		}
	}
	
	private void messageView(TextView messageView, ValidationResult.MessageSeverity severity, int messageId) {
		messageView(messageView, severity, context.getResources().getString(messageId));
	}
	
	private void messageView(TextView messageView, ValidationResult result) {
		messageView(messageView, result.severity(), result.message());
	}
	
	private void messageView(TextView messageView, ValidationResult.MessageSeverity severity, String message) {
		if (message.length() == 0) {
			messageView.setVisibility(View.GONE);
		} else {
			messageView.setVisibility(View.VISIBLE);
		}
		
		messageView.setText(message);
		
		switch (severity) {
		case INFO:
			messageView.setTextColor(0xFFFFFFFF);
			break;
		case WARNING:
			messageView.setTextColor(0xFFF0FF00);
			break;
		case ERROR:
			messageView.setTextColor(0xFFFF0000);
			break;
		}
	}
	
	protected void releaseBuild() {
		//If this is an example, then put the sketch in the "bin" directory within the sketchbook
		final File binFolder = new File((context.isExample() || context.isTemp()) ? context.getSketchbookFolder() : context.getSketchLocation(), "bin");
		
		binFolder.mkdir();
		
		//Clear the console
    	((TextView) context.getEditor().findViewById(R.id.console)).setText("");
		
		builder = new Build(context, BuildContext.create(context));
		builder.setKey(getKeystoreFileUri(), keystorePassword.getText().toString().toCharArray(), (String) alias.getSelectedItem(), aliasPassword.getText().toString().toCharArray());
		
		new Thread(() -> {
			exporting = true;
			builder.build("release", ComponentTarget.deserialize(componentTarget.getSelectedItemPosition()));
			exporting = false;
		}).start();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("export_signed_package");
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
}