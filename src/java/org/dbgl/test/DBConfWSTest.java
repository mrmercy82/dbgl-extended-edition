/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package org.dbgl.test;

import static org.junit.Assert.assertTrue;
import java.util.List;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import org.dbgl.model.entity.SharedConf;
import org.dbgl.service.SettingsService;
import org.junit.Test;


public class DBConfWSTest {

	@Test
	public void testGetEntries() {
		Client client = ClientBuilder.newClient();
		GenericType<List<SharedConf>> confType = new GenericType<List<SharedConf>>() {
		};
		List<SharedConf> confs = client.target(SettingsService.getInstance().getValue("confsharing", "endpoint")).path("/submissions").request().accept(MediaType.APPLICATION_XML).get(confType);
		client.close();

		assertTrue(confs.size() > 500);
	}
}
