package com.calsignlabs.apde.support;

import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kellinwood.security.zipsigner.optional.JksKeyStore;
import kellinwood.security.zipsigner.optional.LoadKeystoreException;

public class InputStreamKeyStore {
	protected static Provider provider = new BouncyCastleProvider();

	public static LoadedKey loadKey(InputStream inputStream, char[] keystorePassword, String alias, char[] keyPassword) throws Exception {
		KeyStore keystore = loadKeyStore(inputStream, keystorePassword);
		Key key = keystore.getKey(alias, keyPassword);
		PrivateKey privateKey = (PrivateKey) key;
		Certificate[] certChain = keystore.getCertificateChain(alias);
		List<X509Certificate> certificates = new ArrayList<>();

		if (certChain != null) {
			for (Certificate certificate : certChain) {
				certificates.add((X509Certificate) certificate);
			}
		} else {
			certificates.add((X509Certificate) keystore.getCertificate(alias));
		}

		return new LoadedKey(privateKey, certificates);
	}

	private static KeyStore loadKeyStore(InputStream inputStream, char[] password) throws Exception {
		KeyStore keystore;

		try {
			keystore = new JksKeyStore();
			keystore.load(inputStream, password);
			inputStream.close();
			return keystore;
		} catch (LoadKeystoreException e) {
			throw e;
		} catch (Exception e) {
			try {
				keystore = KeyStore.getInstance("bks", provider);
				keystore.load(inputStream, password);
				inputStream.close();
				return keystore;
			} catch (Exception x) {
				throw new RuntimeException("Failed to load keystore: " + x.getMessage(), x);
			}
		}
	}

	public static class LoadedKey {
		private final PrivateKey privateKey;
		private final List<X509Certificate> certificates;

		public LoadedKey(PrivateKey privateKey, List<X509Certificate> certificates) {
			this.privateKey = privateKey;
			this.certificates = Collections.unmodifiableList(certificates);
		}

		public PrivateKey getPrivateKey() {
			return privateKey;
		}

		public List<X509Certificate> getCertificates() {
			return certificates;
		}
	}
}
