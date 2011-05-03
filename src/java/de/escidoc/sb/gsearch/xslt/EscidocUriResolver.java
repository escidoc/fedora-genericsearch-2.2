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
 * Copyright 2010 Fachinformationszentrum Karlsruhe Gesellschaft
 * fuer wissenschaftlich-technische Information mbH and Max-Planck-
 * Gesellschaft zur Foerderung der Wissenschaft e.V.
 * All rights reserved.  Use is subject to license terms.
 */

package de.escidoc.sb.gsearch.xslt;

import java.io.InputStream;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import de.escidoc.core.common.util.configuration.EscidocConfiguration;
import dk.defxws.fedoragsearch.server.Config;
import dk.defxws.fedoragsearch.server.URIResolverImpl;

public class EscidocUriResolver extends URIResolverImpl {

    public Source resolve(String href, String base) throws TransformerException {
        try {
            InputStream stylesheet = null;
            //if xsltPath starts with http, get stylesheet from url
            if (href.startsWith("http")) {
                stylesheet = Config.getCurrentConfig().getResourceFromUrl(href);
            } else {
                stylesheet = EscidocUriResolver.class.getResourceAsStream(href);
                if (stylesheet == null) {
                    String searchPropertiesDirectory = EscidocConfiguration.getInstance()
                                .get(EscidocConfiguration.SEARCH_PROPERTIES_DIRECTORY, "search/config");
                    if (searchPropertiesDirectory != null) {
                        if (!searchPropertiesDirectory.startsWith("/")) {
                            searchPropertiesDirectory = "/" + searchPropertiesDirectory;
                        }
                        if (!href.startsWith("/")) {
                            searchPropertiesDirectory += "/";
                        }
                        stylesheet = EscidocUriResolver.class.getResourceAsStream(
                                                    searchPropertiesDirectory + href);
                    }
                }
            }
            if (stylesheet==null) {
                throw new TransformerException("couldnt find resource");
            }

            StreamSource xslt = new StreamSource(stylesheet);
            return xslt;
        } catch (Throwable t) {
            throw new TransformerException("resolve get href="+href+" base="+base, t);
        }
    }
}
