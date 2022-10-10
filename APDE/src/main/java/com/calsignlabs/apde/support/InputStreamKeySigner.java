package com.calsignlabs.apde.support;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import kellinwood.security.zipsigner.ZipSigner;
import kellinwood.security.zipsigner.optional.JksKeyStore;
import kellinwood.security.zipsigner.optional.LoadKeystoreException;

public class InputStreamKeySigner {
	protected static Provider provider = new BouncyCastleProvider();
	
	public static void signZip(ZipSigner zipSigner, InputStream keystoreInputStream, char[] keystorePw, String certAlias, char[] certPw, String signatureAlgorithm, String inputZipFilename, String outputZipFilename) throws Exception {
		zipSigner.issueLoadingCertAndKeysProgressEvent();
		KeyStore keystore = loadKeyStore(keystoreInputStream, keystorePw);
		Certificate cert = keystore.getCertificate(certAlias);
		X509Certificate publicKey = (X509Certificate)cert;
		Key key = keystore.getKey(certAlias, certPw);
		PrivateKey privateKey = (PrivateKey)key;
		zipSigner.setKeys("custom", publicKey, privateKey, signatureAlgorithm, null);
		zipSigner.signZip(inputZipFilename, outputZipFilename);
	}
	
	private static KeyStore loadKeyStore(InputStream fis, char[] password) throws Exception {
		KeyStore ks = null;
		
		try {
			ks = new JksKeyStore();
			ks.load(fis, password);
			fis.close();
			return ks;
		} catch (LoadKeystoreException var6) {
			throw var6;
		} catch (Exception var7) {
			try {
				ks = KeyStore.getInstance("bks", provider);
				ks.load(fis, password);
				fis.close();
				return ks;
			} catch (Exception var5) {
				throw new RuntimeException("Failed to load keystore: " + var5.getMessage(), var5);
			}
		}
	}
}
