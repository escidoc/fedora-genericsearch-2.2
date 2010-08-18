/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at license/ESCIDOC.LICENSE
 * or http://www.escidoc.de/license.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at license/ESCIDOC.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright 2008 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.  
 * All rights reserved.  Use is subject to license terms.
 */
package de.escidoc.sb.util.xml.stax.handler;

import java.util.Vector;

import de.escidoc.core.common.util.stax.StaxParser;
import de.escidoc.core.common.util.xml.stax.events.Attribute;
import de.escidoc.core.common.util.xml.stax.events.EndElement;
import de.escidoc.core.common.util.xml.stax.events.StartElement;
import de.escidoc.core.common.util.xml.stax.handler.DefaultHandler;


public class ObjectAttributeHandler extends DefaultHandler {
    protected StaxParser parser;

    protected Vector<String> attributes = new Vector<String>();
    
    protected StringBuffer thisAttribute = new StringBuffer("");

    protected String elementPath = null;

    protected String attributeName = null;

    protected String attributeNamespaceUri = null;
    
    protected boolean inElementData = false;

    /*
     * 
     */
    public ObjectAttributeHandler(StaxParser parser) {
        this.parser = parser;

    }

    @Override
    public StartElement startElement(StartElement element) {
        String currentPath = parser.getCurPath();
        if (elementPath.equals(currentPath)) {
            inElementData = true;
        }
        if (attributeName != null && !attributeName.equals("")) {
            if (elementPath.equals(currentPath)) {
                int indexOfAttribute = element.indexOfAttribute(
                        attributeNamespaceUri, attributeName);
                if (indexOfAttribute != (-1)) {
                    Attribute att = element.getAttribute(indexOfAttribute);
                    attributes.add(att.getValue());
                }
            }
        }
        return element;
    }

    @Override
    public String characters(final String s, final StartElement element) {
        if (attributeName == null || attributeName.equals("")) {
            if (inElementData) {
                thisAttribute.append(s);
            }
        }
        return s;
    }

    @Override
    public EndElement endElement(EndElement element) {
        String currentPath = parser.getCurPath();
        if (elementPath.equals(currentPath)) {
            attributes.add(thisAttribute.toString());
            thisAttribute = new StringBuffer("");
            inElementData = false;
        }
        return element;
    }

	/**
	 * @return the attributes
	 */
	public Vector<String> getAttributes() {
		return attributes;
	}

    /**
     * @param elementPath the elementPath to set
     */
    public void setElementPath(String elementPath) {
        this.elementPath = elementPath;
    }

    /**
     * @param attributeName the attributeName to set
     */
    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    /**
     * @param attributeNamespaceUri the attributeNamespaceUri to set
     */
    public void setAttributeNamespaceUri(String attributeNamespaceUri) {
        this.attributeNamespaceUri = attributeNamespaceUri;
    }
    

}
