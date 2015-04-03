package org.syncany.plugins.s3;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.syncany.util.StringUtil;

/**
 * @author Christian Roth <christian.roth@port17.de>
 */

public class S3TransferSettingsTest {

	private static final String LOCATIONS_AS_STRING = "ASIA_PACIFIC, ASIA_PACIFIC_NORTHEAST, ASIA_PACIFIC_SINGAPORE, ASIA_PACIFIC_SOUTHEAST, ASIA_PACIFIC_SYDNEY, ASIA_PACIFIC_TOKYO, EU_FRANKFURT, EU_IRELAND, EUROPE, GOVCLOUD_FIPS_US_WEST, GOVCLOUD_US_WEST, SOUTH_AMERICA_EAST, SOUTH_AMERICA_SAO_PAULO, US_WEST, US_WEST_NORTHERN_CALIFORNIA, US_WEST_OREGON";

	@Test
	public void testLocationList() {
		assertEquals(LOCATIONS_AS_STRING, StringUtil.join(Location.values(), ", "));
	}

	@Test
	public void testLocationParse() {
		assertEquals(Location.US_WEST, Enum.valueOf(Location.class, "US_WEST"));
	}

}
