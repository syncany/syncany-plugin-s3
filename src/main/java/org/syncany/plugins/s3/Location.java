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

/**
 * @author Christian Roth <christian.roth@port17.de>
 */
public enum Location {
	// see http://jets3t.s3.amazonaws.com/api/constant-values.html
	ASIA_PACIFIC("ap-southeast-1"),
	ASIA_PACIFIC_NORTHEAST("ap-northeast-1"),
	ASIA_PACIFIC_SINGAPORE("ap-southeast-1"),
	ASIA_PACIFIC_SOUTHEAST("ap-southeast-1"),
	ASIA_PACIFIC_SYDNEY("ap-southeast-2"),
	ASIA_PACIFIC_TOKYO("ap-northeast-1"),
	EU_FRANKFURT("eu-central-1"),
	EU_IRELAND("eu-west-1"),
	EUROPE("EU"),
	GOVCLOUD_FIPS_US_WEST("s3-fips-us-gov-west-1"),
	GOVCLOUD_US_WEST("s3-us-gov-west-1"),
	SOUTH_AMERICA_EAST("sa-east-1"),
	SOUTH_AMERICA_SAO_PAULO("sa-east-1"),
	US_WEST("us-west-1"),
	US_WEST_NORTHERN_CALIFORNIA("us-west-1"),
	US_WEST_OREGON("us-west-2"),

	// not listing experimental google regional locations: https://cloud.google.com/storage/docs/regional-buckets
	GOOGLE_US("US"),
	GOOGLE_ASIA("ASIA"),
	GOOGLE_EU("EU");

	private final String locationId;

	Location(String text) {
		this.locationId = text;
	}

	String getLocationId() {
		return locationId;
	}
}
