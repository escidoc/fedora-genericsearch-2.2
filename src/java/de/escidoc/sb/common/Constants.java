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

package de.escidoc.sb.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Constants for Search and Browse.
 * 
 * @author MIH
 */
public class Constants {

    public static final String XML_CHARACTER_ENCODING = "UTF-8";

    public static final String ORG_UNIT_URL = "/oum/organizational-unit/";

    public static final String ORG_UNIT_PATH_LIST_URL_SUFFIX = "/resources/path-list";

    public static final String ORG_UNIT_PATH_LIST_ROOT_ELEMENT = "organizational-unit-path-list";

    public static final String CONTENT_MODEL_URL = "/cmm/content-model/";

    public static final String CONTENT_MODEL_ROOT_ELEMENT = "content-model";

    public static final String CONTEXT_PROPERTIES_URL = "/ir/context/${CONTEXT_ID}/properties";

    public static final String CONTEXT_PROPERTIES_ROOT_ELEMENT = "properties";

    public static final Pattern CONTEXT_ID_PATTERN = 
		Pattern.compile("\\$\\{CONTEXT_ID\\}");

    public static Matcher CONTEXT_ID_MATCHER = CONTEXT_ID_PATTERN.matcher("");

    public static final String USER_ACCOUNT_URL = "/aa/user-account/";

    public static final String USER_ACCOUNT_ROOT_ELEMENT = "user-account";

    public static final String ITEM_URL = "/ir/item/";

    public static final String CONTAINER_URL = "/ir/container/";

    public static final String CONTAINER_MEMBERS_URL = 
    			"/ir/container/${CONTAINER_ID}/members/filter";

    public static final String CONTAINER_MEMBERS_ROOT_ELEMENT = "member-list";

    public static final Pattern LAST_SLASH_PATTERN = Pattern.compile(".*/(.*)");

    public static final Pattern CONTAINER_ID_PATTERN = 
    				Pattern.compile("\\$\\{CONTAINER_ID\\}");

    public static Matcher CONTAINER_ID_MATCHER = CONTAINER_ID_PATTERN.matcher("");

    public static final String CONTAINER_MEMBER_FILTER = 
    				"<param><filter " +
    				"name=\"http://escidoc.de/core/01/properties/public-status\">" +
    				"${STATUS}" +
    				"</filter><limit>0</limit></param>";

    public static final Pattern STATUS_PATTERN = 
		Pattern.compile("\\$\\{STATUS\\}");

    public static Matcher STATUS_MATCHER = STATUS_PATTERN.matcher("");

}
