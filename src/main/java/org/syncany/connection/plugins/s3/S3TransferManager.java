/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2014 Philipp C. Heckel <philipp.heckel@gmail.com> 
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
package org.syncany.connection.plugins.s3;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.impl.rest.httpclient.RestStorageService;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.StorageBucket;
import org.jets3t.service.model.StorageObject;
import org.syncany.connection.plugins.AbstractTransferManager;
import org.syncany.connection.plugins.ActionRemoteFile;
import org.syncany.connection.plugins.DatabaseRemoteFile;
import org.syncany.connection.plugins.MultiChunkRemoteFile;
import org.syncany.connection.plugins.RemoteFile;
import org.syncany.connection.plugins.RepoRemoteFile;
import org.syncany.connection.plugins.StorageException;
import org.syncany.connection.plugins.TransferManager;

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
 *   <li>The <tt>databases</tt> folder keeps all the {@link DatabaseRemoteFile}s</li>
 *   <li>The <tt>multichunks</tt> folder keeps the actual data within the {@link MultiChunkRemoteFile}s</li>
 * </ul>
 *
 * <p>Concrete implementations of this class must override the {@link #createBucket()} method and the 
 * {@link #createService()} method. 
 * 
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class S3TransferManager extends AbstractTransferManager {
	private static final String APPLICATION_CONTENT_TYPE = "application/x-syncany";
	private static final Logger logger = Logger.getLogger(S3TransferManager.class.getSimpleName());

	private RestStorageService service;
	private StorageBucket bucket;

	private String multichunksPath;
	private String databasesPath;
	private String actionsPath;

	public S3TransferManager(S3Connection connection) {
		super(connection);

		this.multichunksPath = "multichunks";
		this.databasesPath = "databases";
		this.databasesPath = "actions";
	}
	
	@Override
	public S3Connection getConnection() {
		return (S3Connection) super.getConnection();
	}

	@Override
	public void connect() throws StorageException {
		try {
			if (service == null) {
				service = new RestS3Service(getConnection().getCredentials());
			}

			if (bucket == null) {
				bucket = new S3Bucket(getConnection().getBucket(), getConnection().getLocation());
			}
		}
		catch (ServiceException ex) {
			throw new StorageException("Unable to connect to S3: " + ex.getMessage(), ex);
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
		}
		catch (ServiceException e) {
			throw new StorageException(e);
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
			Logger.getLogger(S3TransferManager.class.getName()).log(Level.SEVERE, null, ex);
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
			Logger.getLogger(S3TransferManager.class.getName()).log(Level.SEVERE, null, ex);
			throw new StorageException(ex);
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
						logger.log(Level.INFO, "Cannot create instance of " + remoteFileClass.getSimpleName() + " for object " + simpleRemoteName + "; maybe invalid file name pattern. Ignoring file.");
					}
				}
			}

			return remoteFiles;
		}
		catch (ServiceException ex) {
			logger.log(Level.SEVERE, "Unable to list FTP directory.", ex);
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

	private String getRemoteFilePath(Class<? extends RemoteFile> remoteFile) {
		if (remoteFile.equals(MultiChunkRemoteFile.class)) {
			return multichunksPath;
		}
		else if (remoteFile.equals(DatabaseRemoteFile.class)) {
			return databasesPath;
		}
		else if (remoteFile.equals(ActionRemoteFile.class)) {
			return actionsPath;
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
			tempFileObject.setDataInputStream(new ByteArrayInputStream(new byte[] { 0x01, 0x02, 0x03 }));
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
			String repoRemoteFile = getRemoteFile(new RepoRemoteFile());
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
}
