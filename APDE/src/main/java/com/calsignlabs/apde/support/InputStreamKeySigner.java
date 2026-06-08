package com.calsignlabs.apde.support;

import java.io.InputStream;

import kellinwood.security.zipsigner.ZipSigner;

public class InputStreamKeySigner {
	public static void signZip(ZipSigner zipSigner, InputStream keystoreInputStream, char[] keystorePw, String certAlias, char[] certPw, String signatureAlgorithm, String inputZipFilename, String outputZipFilename) throws Exception {
		zipSigner.issueLoadingCertAndKeysProgressEvent();
		InputStreamKeyStore.LoadedKey key = InputStreamKeyStore.loadKey(keystoreInputStream, keystorePw, certAlias, certPw);
		zipSigner.setKeys("custom", key.getCertificates().get(0), key.getPrivateKey(), signatureAlgorithm, (byte[])null);
		zipSigner.signZip(inputZipFilename, outputZipFilename);
	}
}
