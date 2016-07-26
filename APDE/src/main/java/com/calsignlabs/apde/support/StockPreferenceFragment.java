/***
  Copyright (c) 2008-2012 CommonsWare, LLC
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
  
  From _The Busy Coder's Guide to Android Development_
    http://commonsware.com/Android
 */

package com.calsignlabs.apde.support;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.preference.Preference;

import com.calsignlabs.apde.SettingsActivityHC;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

@SuppressLint("NewApi")
public class StockPreferenceFragment extends PreferenceFragmentCompat {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public void onCreatePreferencesFix(Bundle bundle, String s) {
		String resName = getArguments().getString("resource");
		int res = getActivity().getResources().getIdentifier(resName, "xml", getActivity().getPackageName());
		addPreferencesFromResource(res);
		
		//Hacky way of letting the host activity do what it wants with the preferences
		if(getActivity() instanceof SettingsActivityHC) {
			((SettingsActivityHC) getActivity()).checkPreferences(this);
		}
	}
	
	@Override
	public void onDisplayPreferenceDialog(Preference preference) {
		if (preference instanceof CustomListPreference) {
			DialogFragment fragment = CustomListPreferenceDialogFragmentCompat.newInstance((CustomListPreference) preference);
			fragment.setTargetFragment(this, 0);
			fragment.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
		} else {
			super.onDisplayPreferenceDialog(preference);
		}
	}
}
