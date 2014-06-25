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

import org.syncany.plugins.transfer.TransferManager;
import org.syncany.plugins.transfer.TransferPlugin;
import org.syncany.plugins.transfer.TransferSettings;

/**
 *
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class S3Plugin extends TransferPlugin {
    public S3Plugin() {
    	super("s3");
    }

	@Override
	public TransferManager createTransferManager(TransferSettings connection) {
		return new S3TransferManager((S3TransferSettings) connection);
	}
	
    @Override
    public TransferSettings createSettings() {
        return new S3TransferSettings();
    }
}
