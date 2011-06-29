/*
 *  Copyright (C) 2011 Axel Morgner
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.structr.core.entity.app;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.structr.common.RenderMode;
import org.structr.core.NodeRenderer;
import org.structr.core.entity.AbstractNode;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 * @author Christian Morgner
 */
public class RssItem extends AbstractNode {

	private Map<String, String> values = new LinkedHashMap<String, String>();
	private Node source = null;

	public RssItem() {
	}

	public RssItem(Node node) {

		this.source = node;

		// synthesize type
		values.put(TYPE_KEY, "RssItem");

		initialize();
	}

	@Override
	public Iterable<String> getPropertyKeys()
	{
		return(values.keySet());
	}

	@Override
	public Object getProperty(String key) {

		return(values.get(key));
	}


	// ----- AbstractNode -----
	@Override
	public void initializeRenderers(Map<RenderMode, NodeRenderer> rendererMap) {
	}

	@Override
	public String getIconSrc() {
		return("/images/feed.png");
	}

	@Override
	public void onNodeCreation() {
	}

	@Override
	public void onNodeInstantiation() {
	}

	@Override
	public void onNodeDeletion() {
	}

	// ----- private methods -----
	private void initialize() {

		if(this.source != null) {

			NodeList children = this.source.getChildNodes();
			int len = children.getLength();

			for(int i=0; i<len; i++) {

				Node child = children.item(i);

				String name = child.getNodeName();
				String value = getValue(child);

				if(StringUtils.isNotBlank(name) && StringUtils.isNotBlank(value)) {

					values.put(name, value);
				}
			}

		}
	}

	private String getValue(Node child) {

		if(child.hasChildNodes()) {
			
			return(child.getFirstChild().getNodeValue());

		} else {

			return(child.getNodeValue());
		}
	}
}
