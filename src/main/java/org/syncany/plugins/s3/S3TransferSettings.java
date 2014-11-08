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
package org.syncany.plugins.s3;

import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.security.AWSCredentials;
import org.jets3t.service.security.ProviderCredentials;
import org.simpleframework.xml.Element;
import org.syncany.plugins.transfer.Encrypted;
import org.syncany.plugins.transfer.Setup;
import org.syncany.plugins.transfer.TransferSettings;

public class S3TransferSettings extends TransferSettings {
	@Element(name = "accessKey", required = true)
	@Setup(order = 1, description = "Access Key")
	private String accessKey;

	@Element(name = "secretKey", required = true)
	@Setup(order = 2, sensitive = true, description = "Secret Key")
	@Encrypted
	private String secretKey;
	
	@Element(name = "bucket", required = true)
	@Setup(order = 3, description = "Bucket")
	private String bucket;
	
	@Element(name = "location", required = true)
	@Setup(order = 4, description = "Location")
	private String location = S3Bucket.LOCATION_US_WEST; // cf. http://jets3t.s3.amazonaws.com/api/constant-values.html

	private ProviderCredentials credentials;

	public String getAccessKey() {
		return accessKey;
	}

	public String getBucket() {
		return bucket;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public ProviderCredentials getCredentials() {
		if (credentials == null) {
			credentials = new AWSCredentials(getAccessKey(), getSecretKey());
		}

		return credentials;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}
}
