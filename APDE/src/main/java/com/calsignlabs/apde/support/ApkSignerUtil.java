package com.calsignlabs.apde.support;

import com.android.apksig.ApkSigner;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import kellinwood.security.zipsigner.KeySet;
import kellinwood.security.zipsigner.ZipSigner;

public class ApkSignerUtil {
	public static final String DEBUG_KEY_MODE = "testkey";

	public static void signDebugApk(File inputApk, File outputApk, int minSdkVersion) throws Exception {
		ZipSigner signer = new ZipSigner();
		signer.setKeymode(DEBUG_KEY_MODE);

		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
			signer.signZip(inputApk.getAbsolutePath(), outputApk.getAbsolutePath());
			return;
		}

		KeySet keySet = signer.getKeySet();
		signApk(inputApk, outputApk, DEBUG_KEY_MODE, keySet.getPrivateKey(),
				Collections.singletonList(keySet.getPublicKey()), minSdkVersion, false, true);
	}

	public static void signApk(File inputApk, File outputApk, String signerName,
			PrivateKey privateKey, List<X509Certificate> certificates, int minSdkVersion,
			boolean signV1, boolean debuggable) throws Exception {
		ApkSigner.SignerConfig signerConfig = new ApkSigner.SignerConfig.Builder(
				signerName,
				privateKey,
				certificates
		).build();

		new ApkSigner.Builder(Collections.singletonList(signerConfig))
				.setInputApk(inputApk)
				.setOutputApk(outputApk)
				.setMinSdkVersion(minSdkVersion)
				.setV1SigningEnabled(signV1)
				.setV2SigningEnabled(true)
				.setV3SigningEnabled(false)
				.setV4SigningEnabled(false)
				.setDebuggableApkPermitted(debuggable)
				.build()
				.sign();
	}
}
