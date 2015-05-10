/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2015 Philipp C. Heckel <philipp.heckel@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.plugins.s3;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.GoogleStorageService;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.impl.rest.httpclient.RestStorageService;
import org.jets3t.service.model.GSBucket;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.StorageBucket;
import org.jets3t.service.model.StorageObject;
import org.syncany.config.Config;
import org.syncany.plugins.s3.S3TransferManager.S3ReadAfterWriteConsistentFeatureExtension;
import org.syncany.plugins.transfer.AbstractTransferManager;
import org.syncany.plugins.transfer.StorageException;
import org.syncany.plugins.transfer.StorageMoveException;
import org.syncany.plugins.transfer.TransferManager;
import org.syncany.plugins.transfer.features.ReadAfterWriteConsistent;
import org.syncany.plugins.transfer.features.ReadAfterWriteConsistentFeatureExtension;
import org.syncany.plugins.transfer.files.ActionRemoteFile;
import org.syncany.plugins.transfer.files.CleanupRemoteFile;
import org.syncany.plugins.transfer.files.DatabaseRemoteFile;
import org.syncany.plugins.transfer.files.MultichunkRemoteFile;
import org.syncany.plugins.transfer.files.RemoteFile;
import org.syncany.plugins.transfer.files.SyncanyRemoteFile;
import org.syncany.plugins.transfer.files.TempRemoteFile;
import org.syncany.plugins.transfer.files.TransactionRemoteFile;

/**
 * The REST transfer manager implements a {@link TransferManager} based on
 * a bucket-based storage such as Amazon S3 or Google Storage. It uses the
 * Jets3t library's {@link RestStorageService}.
 *
 * <p>Using a {@link RestConnection}, the transfer manager is configured and uses
 * a {@link StorageBucket} to store the Syncany repository data. While repo and
 * master file are stored in the given folder, databases and multichunks are stored
 * in special sub-folders:
 *
 * <ul>
 * <li>The <tt>databases</tt> folder keeps all the {@link DatabaseRemoteFile}s</li>
 * <li>The <tt>multichunks</tt> folder keeps the actual data within the {@link MultiChunkRemoteFile}s</li>
 * </ul>
 *
 * <p>Concrete implementations of this class must override the {@link #createBucket()} method and the
 * {@link #createService()} method.
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 * @author Christian Roth <christian.roth@port17.de>
 */
@ReadAfterWriteConsistent(extension = S3ReadAfterWriteConsistentFeatureExtension.class)
public class S3TransferManager extends AbstractTransferManager {
	private enum Type {
		GOOGLE, NON_STANDARD, S3
	}

	private static final String APPLICATION_CONTENT_TYPE = "application/x-syncany";
	private static final Logger logger = Logger.getLogger(S3TransferManager.class.getSimpleName());

	private RestStorageService service;
	private StorageBucket bucket;
	private Jets3tProperties jets3tProperties;

	private String multichunksPath;
	private String databasesPath;
	private String actionsPath;
	private String transactionsPath;
	private String tempPath;

	public S3TransferManager(S3TransferSettings connection, Config config) {
		super(connection, config);

		this.multichunksPath = "multichunks";
		this.databasesPath = "databases";
		this.actionsPath = "actions";
		this.transactionsPath = "transactions";
		this.tempPath = "temp";

		// jets3t uses https by default (see https://jets3t.s3.amazonaws.com/toolkit/configuration.html)
		jets3tProperties = Jets3tProperties.getInstance(Constants.JETS3T_PROPERTIES_FILENAME);

		String proxyHost = System.getProperty("https.proxyHost");
		String proxyPort = System.getProperty("https.proxyPort");
		String proxyUser = System.getProperty("https.proxyUser");
		String proxyPassword = System.getProperty("https.proxyPassword");

		if (proxyHost != null && proxyPort != null) {
			jets3tProperties.setProperty("httpclient.proxy-autodetect", "false");
			jets3tProperties.setProperty("httpclient.proxy-host", proxyHost);
			jets3tProperties.setProperty("httpclient.proxy-port", proxyPort);

			if (proxyUser != null && proxyPassword != null) {
				jets3tProperties.setProperty("httpclient.proxy-user", proxyUser);
				jets3tProperties.setProperty("httpclient.proxy-password", proxyPassword);
			}
		}

		switch (getStorageType()) {
		case NON_STANDARD:
			jets3tProperties.setProperty("s3service.s3-endpoint", getSettings().getEndpoint());
			break;
			
		default:
			break;
		}
	}

	public S3TransferSettings getSettings() {
		return (S3TransferSettings) settings;
	}

	@Override
	public void connect() throws StorageException {
		if (service == null) {
			try {
				switch (getStorageType()) {
					case GOOGLE:
						service = new GoogleStorageService(getSettings().getCredentials(), "syncany", null, jets3tProperties);
						break;

					case NON_STANDARD:
					case S3:
						service = new RestS3Service(getSettings().getCredentials(), "syncany", null, jets3tProperties);
						break;
				}
			}
			catch (ServiceException e) {
				throw new StorageException("Invalid service found", e);
			}
		}

		if (bucket == null) {
			switch (getStorageType()) {
				case NON_STANDARD:
					logger.log(Level.INFO, "Using non-standard endpoint, ignoring region.");
					bucket = new S3Bucket(getSettings().getBucket());
					break;

				case GOOGLE:
					logger.log(Level.INFO, "Using Google endpoint, setting location.");
					bucket = new GSBucket(getSettings().getBucket(), getSettings().getLocation().getLocationId());
					break;

				case S3:
					logger.log(Level.INFO, "Using Amazon S3 endpoint, setting location.");
					bucket = new S3Bucket(getSettings().getBucket(), getSettings().getLocation().getLocationId());
					break;
			}
		}
	}

	@Override
	public void disconnect() throws StorageException {
		// Nothing
	}

	@Override
	public void init(boolean createIfRequired) throws StorageException {
		connect();

		try {
			if (!testTargetExists()) {
				service.createBucket(bucket);
			}

			StorageObject multichunkPathFolder = new StorageObject(multichunksPath + "/"); // Slash ('/') makes it a folder
			service.putObject(bucket.getName(), multichunkPathFolder);

			StorageObject databasePathFolder = new StorageObject(databasesPath + "/"); // Slash ('/') makes it a folder
			service.putObject(bucket.getName(), databasePathFolder);

			StorageObject actionPathFolder = new StorageObject(actionsPath + "/"); // Slash ('/') makes it a folder
			service.putObject(bucket.getName(), actionPathFolder);

			StorageObject transactionsPathFolder = new StorageObject(transactionsPath + "/"); // Slash ('/') makes it a folder
			service.putObject(bucket.getName(), transactionsPathFolder);

			StorageObject tempPathFolder = new StorageObject(tempPath + "/"); // Slash ('/') makes it a folder
			service.putObject(bucket.getName(), tempPathFolder);
		}
		catch (ServiceException e) {
			throw new StorageException("Cannot initialize bucket.", e);
		}
	}

	@Override
	public void download(RemoteFile remoteFile, File localFile) throws StorageException {
		connect();

		File tempFile = null;
		String remotePath = getRemoteFile(remoteFile);

		try {
			// Download
			StorageObject fileObj = service.getObject(bucket.getName(), remotePath);
			InputStream fileObjInputStream = fileObj.getDataInputStream();

			logger.log(Level.FINE, "- Downloading from bucket " + bucket.getName() + ": " + fileObj + " ...");
			tempFile = createTempFile(remoteFile.getName());
			FileUtils.copyInputStreamToFile(fileObjInputStream, tempFile);

			fileObjInputStream.close();

			// Move to final location
			if (localFile.exists()) {
				localFile.delete();
			}

			FileUtils.moveFile(tempFile, localFile);
		}
		catch (Exception ex) {
			if (tempFile != null) {
				tempFile.delete();
			}

			throw new StorageException("Unable to download file '" + remoteFile.getName(), ex);
		}
	}

	@Override
	public void upload(File localFile, RemoteFile remoteFile) throws StorageException {
		connect();

		String remotePath = getRemoteFile(remoteFile);

		try {
			StorageObject fileObject = new StorageObject(remotePath);

			fileObject.setContentLength(localFile.length());
			fileObject.setContentType(APPLICATION_CONTENT_TYPE);
			fileObject.setDataInputStream(new FileInputStream(localFile));

			logger.log(Level.FINE, "- Uploading to bucket " + bucket.getName() + ": " + fileObject + " ...");
			service.putObject(bucket.getName(), fileObject);
		}
		catch (Exception ex) {
			logger.log(Level.SEVERE, "Cannot upload " + localFile + " to " + remotePath, ex);
			throw new StorageException(ex);
		}
	}

	@Override
	public boolean delete(RemoteFile remoteFile) throws StorageException {
		connect();

		String remotePath = getRemoteFile(remoteFile);

		try {
			service.deleteObject(bucket.getName(), remotePath);
			return true;
		}
		catch (ServiceException ex) {
			logger.log(Level.SEVERE, "Unable to delete remote file " + remotePath, ex);
			throw new StorageException(ex);
		}
	}

	@Override
	public void move(RemoteFile sourceFile, RemoteFile targetFile) throws StorageException {
		connect();

		String sourceRemotePath = getRemoteFile(sourceFile);
		String targetRemotePath = getRemoteFile(targetFile);

		try {
			StorageObject targetObject = new StorageObject(targetRemotePath);
			service.renameObject(getSettings().getBucket(), sourceRemotePath, targetObject);
		}
		catch (ServiceException ex) {
			logger.log(Level.SEVERE, "Cannot move " + sourceRemotePath + " to " + targetRemotePath, ex);
			throw new StorageMoveException(ex);
		}
	}

	@Override
	public <T extends RemoteFile> Map<String, T> list(Class<T> remoteFileClass) throws StorageException {
		connect();

		try {
			// List folder
			String remoteFilePath = getRemoteFilePath(remoteFileClass);
			String bucketName = bucket.getName();
			StorageObject[] objects = service.listObjects(bucketName, remoteFilePath, null);

			// Create RemoteFile objects
			Map<String, T> remoteFiles = new HashMap<String, T>();

			for (StorageObject storageObject : objects) {
				String simpleRemoteName = storageObject.getName().substring(storageObject.getName().lastIndexOf("/") + 1);

				if (simpleRemoteName.length() > 0) {
					try {
						T remoteFile = RemoteFile.createRemoteFile(simpleRemoteName, remoteFileClass);
						remoteFiles.put(simpleRemoteName, remoteFile);
					}
					catch (Exception e) {
						logger.log(Level.INFO, "Cannot create instance of " + remoteFileClass.getSimpleName() + " for object " + simpleRemoteName
										+ "; maybe invalid file name pattern. Ignoring file.");
					}
				}
			}

			return remoteFiles;
		}
		catch (ServiceException ex) {
			logger.log(Level.SEVERE, "Unable to list S3 bucket.", ex);
			throw new StorageException(ex);
		}
	}

	private String getRemoteFile(RemoteFile remoteFile) {
		String remoteFilePath = getRemoteFilePath(remoteFile.getClass());

		if (remoteFilePath != null) {
			return remoteFilePath + "/" + remoteFile.getName();
		}
		else {
			return remoteFile.getName();
		}
	}

	@Override
	public String getRemoteFilePath(Class<? extends RemoteFile> remoteFile) {
		if (remoteFile.equals(MultichunkRemoteFile.class)) {
			return multichunksPath;
		}
		else if (remoteFile.equals(DatabaseRemoteFile.class) || remoteFile.equals(CleanupRemoteFile.class)) {
			return databasesPath;
		}
		else if (remoteFile.equals(ActionRemoteFile.class)) {
			return actionsPath;
		}
		else if (remoteFile.equals(TransactionRemoteFile.class)) {
			return transactionsPath;
		}
		else if (remoteFile.equals(TempRemoteFile.class)) {
			return tempPath;
		}
		else {
			return null;
		}
	}

	@Override
	public boolean testTargetCanWrite() {
		try {
			String tempRemoteFilePath = "syncany-test-write";

			StorageObject tempFileObject = new StorageObject(tempRemoteFilePath);

			tempFileObject.setContentType(APPLICATION_CONTENT_TYPE);
			tempFileObject.setDataInputStream(new ByteArrayInputStream(new byte[]{0x01, 0x02, 0x03}));
			tempFileObject.setContentLength(3);

			logger.log(Level.FINE, "- Uploading to bucket " + bucket.getName() + ": " + tempFileObject + " ...");
			service.putObject(bucket.getName(), tempFileObject);

			service.deleteObject(bucket.getName(), tempRemoteFilePath);
			logger.log(Level.INFO, "testTargetCanWrite: Success. Repo has write access.");
			return true;
		}
		catch (Exception e) {
			logger.log(Level.INFO, "testTargetCanWrite: Cannot check write status for bucket.", e);
			return false;
		}
	}

	@Override
	public boolean testTargetExists() {
		try {
			if (service.getBucket(bucket.getName()) != null) {
				logger.log(Level.INFO, "testTargetExists: Target exists.");
				return true;
			}
			else {
				logger.log(Level.INFO, "testTargetExists: Target does NOT exist.");
				return false;
			}
		}
		catch (Exception e) {
			logger.log(Level.INFO, "testTargetExists: Target exist test failed with exception.", e);
			return false;
		}
	}

	@Override
	public boolean testTargetCanCreate() {
		try {
			if (testTargetExists()) {
				logger.log(Level.INFO, "testTargetCanCreate: Bucket already exists, so can create returns true.");
				return true;
			}
			else {
				service.createBucket(bucket);
				service.deleteBucket(bucket);

				logger.log(Level.INFO, "testTargetCanCreate: Bucket created/deleted successfully.");
				return true;
			}
		}
		catch (Exception e) {
			logger.log(Level.INFO, "testTargetCanCreate: Target can create test failed with exception.", e);
			return false;
		}
	}

	@Override
	public boolean testRepoFileExists() {
		try {
			String repoRemoteFile = getRemoteFile(new SyncanyRemoteFile());
			StorageObject[] repoFiles = service.listObjects(bucket.getName(), repoRemoteFile, null);

			if (repoFiles != null && repoFiles.length == 1) {
				logger.log(Level.INFO, "testRepoFileExists: Repo file exists.");
				return true;
			}
			else {
				logger.log(Level.INFO, "testRepoFileExists: Repo file does not exist.");
				return false;
			}
		}
		catch (Exception e) {
			logger.log(Level.INFO, "testRepoFileExists: Retrieving repo file list does not exit.", e);
			return false;
		}
	}

	private Type getStorageType() {
		if (getSettings().getEndpoint() != null) {
			logger.log(Level.INFO, "Endpoint is set, assuming s3 non-standard");
			return Type.NON_STANDARD;
		}

		switch (getSettings().getLocation()) {
			case ASIA_PACIFIC:
			case ASIA_PACIFIC_NORTHEAST:
			case ASIA_PACIFIC_SINGAPORE:
			case ASIA_PACIFIC_SOUTHEAST:
			case ASIA_PACIFIC_SYDNEY:
			case ASIA_PACIFIC_TOKYO:
			case EU_FRANKFURT:
			case EU_IRELAND:
			case EUROPE:
			case GOVCLOUD_FIPS_US_WEST:
			case GOVCLOUD_US_WEST:
			case SOUTH_AMERICA_EAST:
			case SOUTH_AMERICA_SAO_PAULO:
			case US_WEST:
			case US_WEST_NORTHERN_CALIFORNIA:
			case US_WEST_OREGON:
				return Type.S3;

			case GOOGLE_US:
			case GOOGLE_ASIA:
			case GOOGLE_EU:
				return Type.GOOGLE;
		}

		throw new IllegalArgumentException("Unknown storage location type " + getSettings().getLocation());
	}

	public static class S3ReadAfterWriteConsistentFeatureExtension implements ReadAfterWriteConsistentFeatureExtension {
		private final S3TransferManager s3TransferManager;

		public S3ReadAfterWriteConsistentFeatureExtension(S3TransferManager s3TransferManager) {
			this.s3TransferManager = s3TransferManager;
		}

		@Override
		public boolean exists(RemoteFile remoteFile) throws StorageException {
			try {
				s3TransferManager.service.getObjectDetails(s3TransferManager.bucket.getName(), s3TransferManager.getRemoteFile(remoteFile));
			}
			catch (ServiceException e) {
				if (e.getResponseCode() == 404) {
					return false;
				}
				else {
					throw new StorageException("Unable to verify if files exists", e);
				}
			}

			return true;
		}
	}
}
