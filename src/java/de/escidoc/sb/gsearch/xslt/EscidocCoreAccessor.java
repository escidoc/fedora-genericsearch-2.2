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

package de.escidoc.sb.gsearch.xslt;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Vector;
import java.util.regex.Matcher;

import javax.naming.AuthenticationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;

import de.escidoc.core.common.util.configuration.EscidocConfiguration;
import de.escidoc.core.common.util.service.ConnectionUtility;
import de.escidoc.core.common.util.stax.StaxParser;
import de.escidoc.sb.common.Constants;
import de.escidoc.sb.util.xml.stax.handler.ContainerMembersCountHandler;
import de.escidoc.sb.util.xml.stax.handler.ContentModelNameHandler;
import de.escidoc.sb.util.xml.stax.handler.ContextNameHandler;
import de.escidoc.sb.util.xml.stax.handler.ObjectAttributeHandler;
import de.escidoc.sb.util.xml.stax.handler.OuHrefHandler;
import de.escidoc.sb.util.xml.stax.handler.UserNameHandler;

/**
 * Is called from sylesheet that transforms foxml to indexable document. Returns
 * xml-structure with all parent-ous of given ou. to call this class from
 * stylesheet: declaration in sylesheet-element:
 * xmlns:organizational-unit-accessor=
 * "xalan://de.escidoc.sb.gsearch.xslt.EscidocCoreAccessor"
 * xmlns:time-helper="xalan://de.escidoc.sb.gsearch.xslt.TimeHelper"
 * extension-element-prefixes= "component-accessor time-helper
 * escidoc-core-accessor" use: <xsl:value-of
 * select="escidoc-core-accessor:getOuParents()"/>
 * 
 * @author MIH
 */
public class EscidocCoreAccessor {
    private static Logger log;

    private static ConnectionUtility connectionUtility;

    private static final String COOKIE_LOGIN = "escidocCookie";

    static {
        log =
            Logger
                .getLogger(
                de.escidoc.sb.gsearch.xslt.EscidocCoreAccessor.class);
    }

    static {
        connectionUtility =
            new ConnectionUtility();
        connectionUtility.setTimeout(300000);
    }

    /**
     * constructor.
     * 
     */
    public EscidocCoreAccessor() {
    }

    /**
     * Calls resource for given rest-uri to get object-xml. 
     * Parses values of elements or attributes given with
     * elementPath, attributeName and attributeNamespaceUri.
     * If accessAsAnonymousUser = 'true', object is retrieved as anonymous user.
     * If getObjidFromHref = 'true', and attributes are hrefs,
     * the objId is parsed out of the href.
     * 
     * @param restUri restUri
     * @param elementPath path to element
     * @param attributeName path to attribute of element (may be empty)
     * @param attributeNamespaceUri namespaceUri of attribute (May be empty)
     * @param accessAsAnonymousUser 'true' or 'false' or empty (default is false) 
     * @param getObjidFromHref 'true' or 'false' or empty (default is false) 
     * 
     * @return String found attributes whitespace-separated
     */
    public static synchronized String getObjectAttribute(
                                        final String restUri, 
                                        final String elementPath,
                                        final String attributeName,
                                        final String attributeNamespaceUri,
                                        final String accessAsAnonymousUser,
                                        final String getObjidFromHref) {

        if (log.isDebugEnabled()) {
            log.debug("executing EscidocCoreAccessor, getObjectAttribute");
        }
        try {
            String result = getXml(restUri, accessAsAnonymousUser);
            if (result == null || "".equals(result)) {
            	return "";
            }
            
            StaxParser sp = new StaxParser();
            ObjectAttributeHandler handler = new ObjectAttributeHandler(sp);
            handler.setAttributeName(attributeName);
            handler.setAttributeNamespaceUri(attributeNamespaceUri);
            handler.setElementPath(elementPath);
            sp.addHandler(handler);

            try {
                sp.parse(new ByteArrayInputStream(result.getBytes(
                                Constants.XML_CHARACTER_ENCODING)));
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            Vector<String> attributes = handler.getAttributes();
            StringBuffer buf = new StringBuffer("");
            Matcher lastSlashMatcher = 
                Constants.LAST_SLASH_PATTERN.matcher("");
            for (String attribute : attributes) {
                if (getObjidFromHref != null 
                        && getObjidFromHref.equals("true")) {
                    lastSlashMatcher.reset(attribute);
                    if (lastSlashMatcher.reset(attribute).matches()) {
                        buf.append(lastSlashMatcher.group(1)).append(" ");
                    }
                } else {
                    buf.append(attribute).append(" ");
                }
            }
            return buf.toString();
        }
        catch (Exception e) {
            log.error("object with uri " + restUri + " not found");
            log.error(e);
        }
        return "";
    }

    /**
     * Parse xml as Dom-Document
     * @param strXml xml as String
     * @return Document Dom-Document
     */
    public static synchronized Document getDomDocument(
                                    final String restUri,
                                    final String accessAsAnonymousUser) {
        String xml = getXml(restUri, accessAsAnonymousUser);
        if (xml == null || "".equals(xml)) {
        	return null;
        }
        Document result = null;
        DocumentBuilderFactory docBuilderFactory =
            DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            result =
                docBuilder
                    .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            return result;
        }
        catch (Exception e) {
            
        }
        return null;
    }

    /**
     * Calls resource for given rest-uri to get xml as String
     * (only works for content stored as xml). 
     * 
     * @param restUri restUri
     * @param accessAsAnonymousUser 'true' or 'false' or empty (default is false) 
     * 
     * @return xml as String
     */
    public static synchronized String getXml(
                                        final String restUri,
                                        final String accessAsAnonymousUser) {

        if (log.isDebugEnabled()) {
            log.debug("executing EscidocCoreAccessor, getXml");
        }
        BasicClientCookie cookie = null;
        try {
            if (accessAsAnonymousUser == null 
                    || !accessAsAnonymousUser.equals("true")) {
                cookie = new BasicClientCookie(COOKIE_LOGIN, 
                        EscidocConfiguration.getInstance().get(
                        EscidocConfiguration.GSEARCH_PASSWORD));
            }
            String domain = "";
            if (!restUri.startsWith("http")) {
                domain = EscidocConfiguration.getInstance().get(
                        EscidocConfiguration.ESCIDOC_CORE_SELFURL);
            }
            String response = connectionUtility.getRequestURLAsString(
                    new URL(domain + restUri), cookie);
            if (response.matches("(?s).*j_spring_security_check.*")) {
            	throw new AuthenticationException("User with handle " 
            			+ EscidocConfiguration.getInstance().get(
                        EscidocConfiguration.GSEARCH_PASSWORD) 
                        + " not allowed to retrieve " + restUri);
            }
            return response;
        }
        catch (Exception e) {
            log.error("object with uri " + restUri + " not found: " + e.getMessage());
        }
        return "";
    }

    /**
     * Calls resource om (object-manager), container-handler
     * to retrieve all members of specified container.
     * Return count of members. 
     * 
     * @param containerId
     *            id of container model
     * @return String member-count
     */
    public static synchronized String getContainerMemberCount(
                    final String containerId, final String status) {

        if (containerId == null || containerId.equals("")) {
            return "";
        }
        
         final String containerObjid = containerId.trim();

        if (log.isDebugEnabled()) {
            log.debug("executing EscidocCoreAccessor, getContainerMemberCount");
        }
        try {
            BasicClientCookie cookie = new BasicClientCookie(
                    COOKIE_LOGIN, EscidocConfiguration.getInstance().get(
                    EscidocConfiguration.GSEARCH_PASSWORD));
            String result = connectionUtility.getRequestURLAsString(
                        new URL(EscidocConfiguration.getInstance().get(
                    EscidocConfiguration.ESCIDOC_CORE_SELFURL) 
                    + Constants.CONTAINER_URL + containerObjid), cookie);

            StaxParser sp = new StaxParser();
            ContainerMembersCountHandler handler = 
                        new ContainerMembersCountHandler(sp);
            sp.addHandler(handler);

            try {
                sp.parse(new ByteArrayInputStream(result.getBytes(
                                Constants.XML_CHARACTER_ENCODING)));
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            return new Integer(handler.getMembersCount()).toString();
        }
        catch (Exception e) {
            log.error("couldnt retrieve member-list for container " 
                                    + containerObjid);
            log.error(e);
        }
        return "";
    }

}
