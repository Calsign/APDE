
package com.calsignlabs.apde.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.preference.ListPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
/**
 * Multi-select list preference. Honeycomb and above provide this functionality natively, but we
 * need backwards-compatibility and some custom features. This gets the job done without much fuss.
 */
public class MultiSelectListPreference extends ListPreference implements OnMultiChoiceClickListener
{
	/** The default delimiter for internal serialization/deserialization. */
	private static final String DEFAULT_DELIMITER = "~";

	/** The default delimiter for external display of the selected values. */
	private static final String DEFAULT_SEPARATOR = ", ";

	/** The delimiter for internal serialization/deserialization. */
	private final String mDelimiter = DEFAULT_DELIMITER;

	/** The delimiter for external display of the selected values. */
	private final String mSeparator = DEFAULT_SEPARATOR;

	/** The state of each checkbox in the list. */
	private boolean[] mCheckedStates;

	/**
	 * Instantiates a new multi-select list preference.
	 *
	 * @param context The current context.
	 * @param attrs The attributes provided from the xml resource.
	 */
	public MultiSelectListPreference( Context context, AttributeSet attrs )
	{
		super( context, attrs );
	}

	/**
	 * Gets the selected values.
	 *
	 * @return The selected values.
	 */
	public List<String> getSelectedValues()
	{
		List<String> selectedValues = new ArrayList<String>();
		CharSequence[] allValues = getEntryValues();
		for( int i = 0; i < allValues.length; i++ )
		{
			if( mCheckedStates[i] )
				selectedValues.add( allValues[i].toString() );
		}
		return selectedValues;
	}

	/**
	 * Gets the selected entries.
	 *
	 * @return The selected entries.
	 */
	public List<CharSequence> getSelectedEntries()
	{
		List<CharSequence> selectedEntries = new ArrayList<CharSequence>();
		CharSequence[] allEntries = getEntries();
		
		for( int i = 0; i < allEntries.length; i++ )
		{
			if( mCheckedStates[i] )
				selectedEntries.add( allEntries[i] );
		}
		return selectedEntries;
	}

	/**
	 * Gets the selected entries as a concatenated string.
	 *
	 * @return The selected entries as a string.
	 */
	public CharSequence getSelectedEntriesString()
	{
		return TextUtils.join( mSeparator, getSelectedEntries() );
	}

	@Override
	public void setEntries( CharSequence[] entries )
	{
		super.setEntries( entries );
		synchronizeState( getValue() );
	}

	@Override
	public void setValue( String value )
	{
		super.setValue( value );
		synchronizeState( getValue() );
	}

	@Override
	protected void onPrepareDialogBuilder( Builder builder )
	{
		synchronizeState( getValue() );
		
		builder.setMultiChoiceItems( getEntries(), mCheckedStates, this );
		//builder.setAdapter(new ArrayAdapter<String>(getContext(), R.id.permissions_list_item), this);
	}
	
//	@Override
//	protected View onCreateDialogView() {
//		View view = View.inflate(getContext(), R.layout.dialog_permissions, null);
//		
//		((TextView) view.findViewById(android.R.id.title)).setText(R.string.prop_permissions);
//
//		ListView list = (ListView) view.findViewById(android.R.id.list);
//		ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
//				getContext(), android.R.id.checkbox,
//				getEntries());
//
//		list.setAdapter(adapter);
//		list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
//		list.setItemChecked(findIndexOfValue(getValue()), true);
//		//list.setOnItemClickListener(this);
//
//		return view;
//	}
	
//	private class ListItemAdapter extends ArrayAdapter<String> {
//		public ListItemAdapter(Context context, int id) {
//			super(context, id);
//		}
//		
//		@Override
//		public View getView(int position, View convertView, ViewGroup parent) {
//			View view = super.getView(position, convertView, parent);
//			TextView title = (TextView) view.findViewById(android.R.id.title);
//			title.setTextSize(12);
//			
//			System.out.println(title.getText());
//			
//			return view;
//		}
//	}

	@Override
	public void onClick( DialogInterface dialog, int which, boolean isChecked )
	{
		mCheckedStates[which] = isChecked;
	}

	@Override
	protected void onDialogClosed( boolean positiveResult )
	{
		String newValue = serialize( getSelectedValues(), mDelimiter );
		
		//if( positiveResult && callChangeListener( newValue ) )
		{ //save changes no matter what because we aren't displaying OK / Cancel buttons
			// Persist the new value
			setValue( newValue );
		}
	}

	/**
	 * Synchronize internal state and summary with the selected and available values.
	 */
	private void synchronizeState( String value )
	{
		List<String> selectedValues = deserialize( value, mDelimiter );
		CharSequence[] allValues = getEntryValues();
		mCheckedStates = new boolean[allValues.length];
		for( int i = 0; i < allValues.length; i++ )
		{
			mCheckedStates[i] = selectedValues.contains( allValues[i].toString() );
		}
		setSummary( getSelectedEntriesString() );
	}

	/**
	 * Deserialize the selected values array from a string.
	 *
	 * @param value The serialized value of the array.
	 * @param delimiter The delimiter used between array elements in the serialization.
	 *
	 * @return The array of selected values.
	 */
	public static List<String> deserialize( String value, String delimiter )
	{
		return Arrays.asList( value.split( delimiter ) );
	}

	/**
	 * Deserialize the selected values array from a string using the default delimiter.
	 *
	 * @param value The serialized value of the array.
	 *
	 * @return The array of selected values.
	 */
	public static List<String> deserialize( String value )
	{
		return deserialize( value, DEFAULT_DELIMITER );
	}

	/**
	 * Serialize the selected values array to a string.
	 *
	 * @param selectedValues The array of selected values.
	 * @param delimiter The delimiter used between array elements in the serialization.
	 *
	 * @return The serialized value of the array.
	 */
	public static String serialize( List<String> selectedValues, String delimiter )
	{
		return selectedValues == null ? "" : TextUtils.join( delimiter, selectedValues );
	}

	/**
	 * Serialize the selected values array to a string using the default delimiter.
	 *
	 * @param selectedValues The array of selected values.
	 *
	 * @return The serialized value of the array.
	 */
	public static String serialize( List<String> selectedValues )
	{
		return serialize( selectedValues, DEFAULT_DELIMITER );
	}
}