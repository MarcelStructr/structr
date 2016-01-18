/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.cron;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.agent.Task;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.Principal;
import org.structr.web.entity.feed.DataFeed;

/**
 *
 */


public class UpdateFeedTask<T extends DataFeed> implements Task<T> {

	@Override
	public Principal getUser() {
		return null;
	}

	@Override
	public List<T> getNodes() {
		
		try {
			return (List<T>) StructrApp.getInstance().get(DataFeed.class);
		} catch (FrameworkException ex) {
			Logger.getLogger(UpdateFeedTask.class.getName()).log(Level.SEVERE, null, ex);
			ex.printStackTrace();
		}
		
		return Collections.EMPTY_LIST;
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public Date getScheduledTime() {
		return new Date();
	}

	@Override
	public Date getCreationTime() {
		return new Date();
	}

	@Override
	public String getType() {
		return "UpdateFeedTask";
	}

	@Override
	public long getDelay(TimeUnit unit) {
		return 0;
	}

	@Override
	public int compareTo(Delayed o) {
		return 0;
	}

	@Override
	public Object getStatusProperty(String key) {
		return null;
	}
	
}
