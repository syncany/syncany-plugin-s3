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
	US_WEST_OREGON("us-west-2");

	private final String locationId;

	Location(String text) {
		this.locationId = text;
	}

	String getLocationId() {
		return locationId;
	}
}
