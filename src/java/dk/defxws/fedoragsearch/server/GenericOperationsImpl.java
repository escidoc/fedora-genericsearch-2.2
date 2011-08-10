//$Id: GenericOperationsImpl.java 7828 2008-11-12 13:57:09Z gertsp $
/*
 * <p><b>License and Copyright: </b>The contents of this file is subject to the
 * same open source license as the Fedora Repository System at www.fedora-commons.org
 * Copyright &copy; 2006, 2007, 2008 by The Technical University of Denmark.
 * All rights reserved.</p>
 */
package dk.defxws.fedoragsearch.server;

import static com.yourmediashelf.fedora.client.FedoraClient.export;
import static com.yourmediashelf.fedora.client.FedoraClient.getDatastreamDissemination;
import static com.yourmediashelf.fedora.client.FedoraClient.getDissemination;
import static com.yourmediashelf.fedora.client.FedoraClient.listDatastreams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;

import com.yourmediashelf.fedora.client.FedoraClientException;
import com.yourmediashelf.fedora.client.FedoraCredentials;
import com.yourmediashelf.fedora.client.request.GetDissemination;
import com.yourmediashelf.fedora.client.response.FedoraResponse;
import com.yourmediashelf.fedora.client.response.ListDatastreamsResponse;
import com.yourmediashelf.fedora.generated.access.DatastreamType;

import dk.defxws.fedoragsearch.server.errors.ConfigException;
import dk.defxws.fedoragsearch.server.errors.FedoraObjectNotFoundException;
import dk.defxws.fedoragsearch.server.errors.GenericSearchException;
import dk.defxws.fedoragsearch.server.utils.IOUtils;
import dk.defxws.fedoragsearch.server.utils.Stream;
import fedora.client.FedoraClient;
import fedora.common.Constants;
import fedora.server.access.FedoraAPIA;
import fedora.server.management.FedoraAPIM;

/**
 * performs the generic parts of the operations
 * 
 * @author  gsp@dtv.dk
 * @version 
 */
public class GenericOperationsImpl implements Operations {
    
    private static final Logger logger =
        Logger.getLogger(GenericOperationsImpl.class);

    private static final Map fedoraClients = new HashMap();

    private static final Map fedoraRestClients = new HashMap();

    protected String fgsUserName;
    protected String indexName;
    protected Config config;
    protected SearchResultFiltering srf;
    protected int insertTotal = 0;
    protected int updateTotal = 0;
    protected int deleteTotal = 0;
    protected int docCount = 0;
    protected int warnCount = 0;
    
    protected byte[] foxmlRecord;
    protected String dsID;
    protected String dsText;
    protected String[] params = null;

    private static FedoraClient getFedoraClient(
    		String repositoryName,
    		String fedoraSoap,
    		String fedoraUser,
    		String fedoraPass)
            throws GenericSearchException {
        try {
            String baseURL = getBaseURL(fedoraSoap);
            String user = fedoraUser; 
            String clientId = user + "@" + baseURL;
            synchronized (fedoraClients) {
                if (fedoraClients.containsKey(clientId)) {
                    return (FedoraClient) fedoraClients.get(clientId);
                } else {
                    FedoraClient client = new FedoraClient(baseURL,
                            user, fedoraPass);
                    //MIH: modifiy connection-parameters//////////////////////
                    client.MAX_CONNECTIONS_PER_HOST = 1500;
                    client.MAX_TOTAL_CONNECTIONS = 1500;
                    client.SOCKET_TIMEOUT_SECONDS = 1800;
                    client.TIMEOUT_SECONDS = 1800;
                    //////////////////////////////////////////////////////////
                    fedoraClients.put(clientId, client);
                    return client;
                }
            }
        } catch (Exception e) {
            throw new GenericSearchException("Error getting FedoraClient"
                    + " for repository: " + repositoryName, e);
        }
    }

    //MIH: Added for REST-Access
    private static com.yourmediashelf.fedora.client.FedoraClient getRestFedoraClient(
    		String repositoryName,
    		String fedoraRest,
    		String fedoraUser,
    		String fedoraPass)
            throws GenericSearchException {
        try {
            String user = fedoraUser; 
            String clientId = user + "@" + fedoraRest;
            synchronized (fedoraRestClients) {
                if (fedoraRestClients.containsKey(clientId)) {
                    return (com.yourmediashelf.fedora.client.FedoraClient) fedoraRestClients.get(clientId);
                } else {
                	com.yourmediashelf.fedora.client.FedoraClient restClient = 
                		new com.yourmediashelf.fedora.client.FedoraClient(
                				new FedoraCredentials(new URL(fedoraRest), user, fedoraPass));
                    fedoraRestClients.put(clientId, restClient);
                    return restClient;
                }
            }
        } catch (Exception e) {
            throw new GenericSearchException("Error getting FedoraRestClient"
                    + " for repository: " + repositoryName, e);
        }
    }

    private static String getBaseURL(String fedoraSoap)
            throws Exception {
        final String end = "/services";
        String baseURL = fedoraSoap;
        if (fedoraSoap.endsWith(end)) {
            return fedoraSoap.substring(0, fedoraSoap.length() - end.length());
        } else {
            throw new Exception("Unable to determine baseURL from fedoraSoap"
                    + " value (expected it to end with '" + end + "'): "
                    + fedoraSoap);
        }
    }

    private static FedoraAPIA getAPIA(
    		String repositoryName,
    		String fedoraSoap,
    		String fedoraUser,
    		String fedoraPass,
    		String trustStorePath,
    		String trustStorePass)
    throws GenericSearchException {
    	if (trustStorePath!=null)
    		System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    	if (trustStorePass!=null)
    		System.setProperty("javax.net.ssl.trustStorePassword", trustStorePass);
    	FedoraClient client = getFedoraClient(repositoryName, fedoraSoap, fedoraUser, fedoraPass);
    	try {
    		return client.getAPIA();
    	} catch (Exception e) {
    		throw new GenericSearchException("Error getting API-A stub"
    				+ " for repository: " + repositoryName, e);
    	}
    }
    
    private static FedoraAPIM getAPIM(
    		String repositoryName,
    		String fedoraSoap,
    		String fedoraUser,
    		String fedoraPass,
    		String trustStorePath,
    		String trustStorePass)
    throws GenericSearchException {
    	if (trustStorePath!=null)
    		System.setProperty("javax.net.ssl.trustStore", trustStorePath);
    	if (trustStorePass!=null)
    		System.setProperty("javax.net.ssl.trustStorePassword", trustStorePass);
    	FedoraClient client = getFedoraClient(repositoryName, fedoraSoap, fedoraUser, fedoraPass);
    	try {
    		return client.getAPIM();
    	} catch (Exception e) {
    		throw new GenericSearchException("Error getting API-M stub"
    				+ " for repository: " + repositoryName, e);
    	}
    }
    
    public void init(String indexName, Config currentConfig) {
    	init(null, indexName, currentConfig);
    }
    
    public void init(String fgsUserName, String indexName, Config currentConfig) {
    	this.fgsUserName = fgsUserName;
    	this.indexName = indexName;
        config = currentConfig;
        if (null==this.fgsUserName || this.fgsUserName.length()==0) {
        	try {
				this.fgsUserName = config.getProperty("fedoragsearch.testUserName");
			} catch (ConfigException e) {
				this.fgsUserName = "fedoragsearch.testUserName";
			}
        }
    }

    public String gfindObjects(
            String query,
            int hitPageStart,
            int hitPageSize,
            int snippetsMax,
            int fieldMaxLength,
            String indexName,
            String sortFields,
            String resultPageXslt)
    throws java.rmi.RemoteException {
        
        if (logger.isDebugEnabled())
            logger.debug("gfindObjects" +
                    " query="+query+
                    " hitPageStart="+hitPageStart+
                    " hitPageSize="+hitPageSize+
                    " snippetsMax="+snippetsMax+
                    " fieldMaxLength="+fieldMaxLength+
                    " indexName="+indexName+
                    " sortFields="+sortFields+
                    " resultPageXslt="+resultPageXslt);
        srf = config.getSearchResultFiltering();
        params = new String[18];
        params[0] = "OPERATION";
        params[1] = "gfindObjects";
        params[2] = "QUERY";
        params[3] = query;
        params[4] = "HITPAGESTART";
        params[5] = Integer.toString(hitPageStart);
        params[6] = "HITPAGESIZE";
        params[7] = Integer.toString(hitPageSize);
        params[8] = "INDEXNAME";
        params[9] = indexName;
        params[10] = "SORTFIELDS";
        params[11] = sortFields;
        params[14] = "FGSUSERNAME";
        params[15] = fgsUserName;
        params[16] = "SRFTYPE";
        params[17] = config.getSearchResultFilteringType();
        return "";
    }
    
    public String browseIndex(
            String startTerm,
            int termPageSize,
            String fieldName,
            String indexName,
            String resultPageXslt)
    throws java.rmi.RemoteException {
        
        if (logger.isDebugEnabled())
            logger.debug("browseIndex" +
                    " startTerm="+startTerm+
                    " termPageSize="+termPageSize+
                    " fieldName="+fieldName+
                    " indexName="+indexName+
                    " resultPageXslt="+resultPageXslt);
        params = new String[12];
        params[0] = "OPERATION";
        params[1] = "browseIndex";
        params[2] = "STARTTERM";
        params[3] = startTerm;
        params[4] = "TERMPAGESIZE";
        params[5] = Integer.toString(termPageSize);
        params[6] = "INDEXNAME";
        params[7] = indexName;
        params[8] = "FIELDNAME";
        params[9] = fieldName;
        return "";
    }
    
    public String getRepositoryInfo(
            String repositoryName,
            String resultPageXslt) throws java.rmi.RemoteException {
        if (logger.isDebugEnabled())
            logger.debug("getRepositoryInfo" +
                    " repositoryName="+repositoryName+
                    " resultPageXslt="+resultPageXslt);
        InputStream repositoryStream =  null;
        String repositoryInfoPath = "/"+config.getConfigName()+"/repository/"+config.getRepositoryName(repositoryName)+"/repositoryInfo.xml";
        try {
            repositoryStream =  this.getClass().getResourceAsStream(repositoryInfoPath);
            if (repositoryStream == null) {
                throw new GenericSearchException("Error "+repositoryInfoPath+" not found in classpath");
            }
        } catch (IOException e) {
            throw new GenericSearchException("Error "+repositoryInfoPath+" not found in classpath", e);
        }
        String xsltPath = config.getConfigName()
        		+"/repository/"+config.getRepositoryName(repositoryName)+"/"
        		+config.getRepositoryInfoResultXslt(repositoryName, resultPageXslt);
        Stream sb = (new GTransformer()).transform(
        		xsltPath,
                new StreamSource(repositoryStream),
                new String[] {});
        return IOUtils.convertStreamToStringBuffer(sb).toString();
    }
    
    public String getIndexInfo(
            String indexName,
            String resultPageXslt) throws java.rmi.RemoteException {
        if (logger.isDebugEnabled())
            logger.debug("getIndexInfo" +
                    " indexName="+indexName+
                    " resultPageXslt="+resultPageXslt);
        return "";
    }
    
    public String updateIndex(
            String action,
            String value,
            String repositoryNameParam,
            String indexNames,
            String indexDocXslt,
            String resultPageXslt)
    throws java.rmi.RemoteException {
        if (logger.isDebugEnabled())
            logger.debug("updateIndex" +
                    " action="+action+
                    " value="+value+
                    " repositoryName="+repositoryNameParam+
                    " indexNames="+indexNames+
                    " indexDocXslt="+indexDocXslt+
                    " resultPageXslt="+resultPageXslt);
        StringBuffer resultXml = new StringBuffer();
        String repositoryName = repositoryNameParam;
        if (repositoryNameParam==null || repositoryNameParam.equals(""))
        	repositoryName = config.getRepositoryName(repositoryName);
        resultXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        resultXml.append("<resultPage");
        resultXml.append(" operation=\"updateIndex\"");
        resultXml.append(" action=\"" + action + "\"");
        resultXml.append(" value=\"" + value + "\"");
        resultXml.append(" repositoryName=\"" + repositoryName + "\"");
        resultXml.append(" indexNames=\"" + indexNames + "\"");
        resultXml.append(" resultPageXslt=\"" + resultPageXslt + "\"");
        resultXml.append(" dateTime=\"" + new Date() + "\"");
        resultXml.append(">\n");
        StringTokenizer st = new StringTokenizer(config.getIndexNames(indexNames));
        while (st.hasMoreTokens()) {
            String indexName = st.nextToken();
            Operations ops = config.getOperationsImpl(fgsUserName, indexName);
            resultXml.append(ops.updateIndex(action, value, repositoryName, indexName, indexDocXslt, resultPageXslt));
        }
        resultXml.append("</resultPage>\n");
        if (logger.isDebugEnabled())
            logger.debug("resultXml="+resultXml);
        return resultXml.toString();
    }
    
    public void getFoxmlFromPid(
            String pid,
            String repositoryName)
    throws java.rmi.RemoteException {
        
        if (logger.isInfoEnabled())
            logger.info("getFoxmlFromPid" +
                    " pid="+pid +
                    " repositoryName="+repositoryName);
        String fedoraVersion = config.getFedoraVersion(repositoryName);
        String format = Constants.FOXML1_1.uri;
        if(fedoraVersion != null && fedoraVersion.startsWith("2.")) {
            format = Constants.FOXML1_0_LEGACY;
        }
//        FedoraAPIM apim = getAPIM(repositoryName, 
//        		config.getFedoraSoap(repositoryName), 
//        		config.getFedoraUser(repositoryName), 
//        		config.getFedoraPass(repositoryName), 
//        		config.getTrustStorePath(repositoryName), 
//        		config.getTrustStorePass(repositoryName) );
//        
//        try {
//        	foxmlRecord = apim.export(pid, format, "public");
//        } catch (RemoteException e) {
//        	throw new FedoraObjectNotFoundException("Fedora Object "+pid+" not found at "+repositoryName, e);
//        }
    	//MIH: REST
        InputStream inStr = null;
        ByteArrayOutputStream out = null;
        try {
        	com.yourmediashelf.fedora.client.FedoraClient restClient = 
        		getRestFedoraClient(
            		repositoryName, 
            		config.getFedoraRest(repositoryName), 
            		config.getFedoraUser(repositoryName),
            		config.getFedoraPass(repositoryName) );
        	FedoraResponse response = export(pid).format(format)
        					.context("public").execute(restClient);
            inStr = response.getEntityInputStream();
            out = new ByteArrayOutputStream();
            if (inStr != null) {
                byte[] bytes = new byte[0xFFFF];
                int i = -1;
                while ((i = inStr.read(bytes)) > -1) {
                    out.write(bytes, 0, i);
                }
                out.flush();
                foxmlRecord = out.toByteArray();
            }

        } catch (FedoraClientException e) {
        	throw new FedoraObjectNotFoundException("Fedora Object "+pid+" not found at "+repositoryName, e);
        } catch (IOException e) {
            throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
        } finally {
        	if (inStr != null) {
        		try {
        			inStr.close();
        		} catch (IOException e) {}
        	}
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
            }
        }
    }
    
    public Stream getDatastreamText(
            String pid,
            String repositoryName,
            String dsId)
    throws GenericSearchException {
//    	return getDatastreamText(pid, repositoryName, dsId,
//                		config.getFedoraSoap(repositoryName), 
//                		config.getFedoraUser(repositoryName), 
//                		config.getFedoraPass(repositoryName), 
//                		config.getTrustStorePath(repositoryName), 
//                		config.getTrustStorePass(repositoryName) );
    	//MIH: REST
    	return getDatastreamText(pid, repositoryName, dsId,
        		config.getFedoraRest(repositoryName), 
        		config.getFedoraUser(repositoryName), 
        		config.getFedoraPass(repositoryName), 
        		config.getTrustStorePath(repositoryName), 
        		config.getTrustStorePass(repositoryName) );
    }
    
    public Stream getDatastreamText(
            String pid,
            String repositoryName,
            String dsId,
    		String fedoraSoap,
    		String fedoraUser,
    		String fedoraPass,
    		String trustStorePath,
    		String trustStorePass)
    throws GenericSearchException {
        if (logger.isInfoEnabled())
            logger.info("getDatastreamText"
            		+" pid="+pid
            		+" repositoryName="+repositoryName
            		+" dsId="+dsId
            		+" fedoraSoap="+fedoraSoap
            		+" fedoraUser="+fedoraUser
            		+" fedoraPass="+fedoraPass
            		+" trustStorePath="+trustStorePath
            		+" trustStorePass="+trustStorePass);
        Stream dsBuffer = new Stream();
        String mimetype = "";
    	FedoraResponse response = null;
        if (dsId != null) {
//            try {
//                FedoraAPIA apia = getAPIA(
//                		repositoryName, 
//                		fedoraSoap, 
//                		fedoraUser,
//                		fedoraPass,
//                		trustStorePath,
//                		trustStorePass );
//                MIMETypedStream mts = apia.getDatastreamDissemination(pid, 
//                        dsId, null);
//                if (mts==null) return "";
//                ds = mts.getStream();
//                mimetype = mts.getMIMEType();
//            } catch (AxisFault e) {
//                if (e.getFaultString().indexOf("DatastreamNotFoundException")>-1 ||
//                        e.getFaultString().indexOf("DefaulAccess")>-1)
//                    return new String();
//                else
//                    throw new GenericSearchException(e.getFaultString()+": "+e.toString());
//            } catch (RemoteException e) {
//                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
//            }
        	//MIH: REST
            try {
            	com.yourmediashelf.fedora.client.FedoraClient restClient = 
            		getRestFedoraClient(
                		repositoryName, 
                		fedoraSoap, 
                		fedoraUser,
                		fedoraPass );
            	response = getDatastreamDissemination(pid, dsId).execute(restClient);
                mimetype = response.getMimeType();

            } catch (FedoraClientException e) {
                if (e.getMessage().indexOf("no path")>-1 ||
                        e.getMessage().indexOf("DefaulAccess")>-1)
                    return new Stream();
                else
                    throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
            } catch (IOException e) {
                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
            }
        }
        if (response != null && response.getEntityInputStream() != null) {
            dsBuffer = (TransformerToText.getText(response.getEntityInputStream(), mimetype));
        }
        if (logger.isDebugEnabled())
            logger.debug("getDatastreamText" +
                    " pid="+pid+
                    " dsId="+dsId+
                    " mimetype="+mimetype+
                    " dsBuffer="+dsBuffer.toString());
        return dsBuffer;
    }
    
    public Stream getFirstDatastreamText(
            String pid,
            String repositoryName,
            String dsMimetypes)
    throws GenericSearchException {
//    	return getFirstDatastreamText(pid, repositoryName, dsMimetypes,
//            		config.getFedoraSoap(repositoryName), 
//            		config.getFedoraUser(repositoryName), 
//            		config.getFedoraPass(repositoryName), 
//            		config.getTrustStorePath(repositoryName), 
//            		config.getTrustStorePass(repositoryName));
    	//MIH: REST
    	return getFirstDatastreamText(pid, repositoryName, dsMimetypes,
        		config.getFedoraRest(repositoryName), 
        		config.getFedoraUser(repositoryName), 
        		config.getFedoraPass(repositoryName), 
        		config.getTrustStorePath(repositoryName), 
        		config.getTrustStorePass(repositoryName));
    }
    
    public Stream getFirstDatastreamText(
            String pid,
            String repositoryName,
            String dsMimetypes,
    		String fedoraSoap,
    		String fedoraUser,
    		String fedoraPass,
    		String trustStorePath,
    		String trustStorePass)
    throws GenericSearchException {
        if (logger.isInfoEnabled())
            logger.info("getFirstDatastreamText"
                    +" pid="+pid
            		+" dsMimetypes="+dsMimetypes
            		+" fedoraSoap="+fedoraSoap
            		+" fedoraUser="+fedoraUser
            		+" fedoraPass="+fedoraPass
            		+" trustStorePath="+trustStorePath
            		+" trustStorePass="+trustStorePass);
        Stream dsBuffer = new Stream();
//        Datastream[] dsds = null;
//        try {
//            FedoraAPIM apim = getAPIM(
//            		repositoryName, 
//            		fedoraSoap, 
//            		fedoraUser,
//            		fedoraPass,
//            		trustStorePath,
//            		trustStorePass );
//            dsds = apim.getDatastreams(pid, null, "A");
//        } catch (AxisFault e) {
//            throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
//        } catch (RemoteException e) {
//            throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
//        }
        //MIH: REST
        List<DatastreamType> datastreams = null;
        try {
        	com.yourmediashelf.fedora.client.FedoraClient restClient = 
        		getRestFedoraClient(
            		repositoryName, 
            		fedoraSoap, 
            		fedoraUser,
            		fedoraPass );
        	ListDatastreamsResponse response = listDatastreams(pid).execute(restClient);
            datastreams = response.getDatastreams();
        } catch (FedoraClientException e) {
        	throw new FedoraObjectNotFoundException("Fedora Object "+pid+" not found at "+repositoryName, e);
        } catch (IOException e) {
            throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
        }
        String mimetypes = config.getMimeTypes();
        if (dsMimetypes!=null && dsMimetypes.length()>0)
            mimetypes = dsMimetypes;
        String mimetype = "";
        dsID = null;
        if (datastreams != null && datastreams.size() > 0) {
            int best = 99999;
            for (DatastreamType d : datastreams) {
                int j = mimetypes.indexOf(d.getMimeType());
                if (j > -1 && best > j) {
                    dsID = d.getDsid();
                    best = j;
                    mimetype = d.getMimeType();
                }
            }
        }
    	FedoraResponse response = null;
        if (dsID != null) {
//            try {
//                FedoraAPIA apia = getAPIA(
//                		repositoryName, 
//                		fedoraSoap, 
//                		fedoraUser,
//                		fedoraPass,
//                		trustStorePath,
//                		trustStorePass );
//                MIMETypedStream mts = apia.getDatastreamDissemination(pid, 
//                        dsID, null);
//                ds = mts.getStream();
//            } catch (AxisFault e) {
//                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
//            } catch (RemoteException e) {
//                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
//            }
        	//MIH: REST
            try {
            	com.yourmediashelf.fedora.client.FedoraClient restClient = 
            		getRestFedoraClient(
                		repositoryName, 
                		fedoraSoap, 
                		fedoraUser,
                		fedoraPass );
            	response = getDatastreamDissemination(pid, dsID).execute(restClient);
                mimetype = response.getMimeType();

            } catch (FedoraClientException e) {
                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
            } catch (IOException e) {
                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
            }
        }
        if (response != null && response.getEntityInputStream() != null) {
            dsBuffer = (TransformerToText.getText(response.getEntityInputStream(), mimetype));
        }
        if (logger.isDebugEnabled())
            logger.debug("getFirstDatastreamText" +
                    " pid="+pid+
                    " dsID="+dsID+
                    " mimetype="+mimetype+
                    " dsBuffer="+dsBuffer.toString());
        return dsBuffer;
    }
    
    public Stream getDisseminationText(
            String pid,
            String repositoryName,
            String bDefPid, 
            String methodName, 
            String parameters, 
            String asOfDateTime)
    throws GenericSearchException {
//    	return getDisseminationText(pid, repositoryName, bDefPid, methodName, parameters, asOfDateTime,
//                		config.getFedoraSoap(repositoryName), 
//                		config.getFedoraUser(repositoryName), 
//                		config.getFedoraPass(repositoryName), 
//                		config.getTrustStorePath(repositoryName), 
//                		config.getTrustStorePass(repositoryName) );
    	//MIH: REST
    	return getDisseminationText(pid, repositoryName, bDefPid, methodName, parameters, asOfDateTime,
        		config.getFedoraRest(repositoryName), 
        		config.getFedoraUser(repositoryName), 
        		config.getFedoraPass(repositoryName), 
        		config.getTrustStorePath(repositoryName), 
        		config.getTrustStorePass(repositoryName) );
    }
    
    public Stream getDisseminationText(
            String pid,
            String repositoryName,
            String bDefPid, 
            String methodName, 
            String parameters, 
            String asOfDateTime,
    		String fedoraSoap,
    		String fedoraUser,
    		String fedoraPass,
    		String trustStorePath,
    		String trustStorePass)
    throws GenericSearchException {
        if (logger.isInfoEnabled())
            logger.info("getDisseminationText" +
                    " pid="+pid+
                    " bDefPid="+bDefPid+
                    " methodName="+methodName+
                    " parameters="+parameters+
                    " asOfDateTime="+asOfDateTime
            		+" fedoraSoap="+fedoraSoap
            		+" fedoraUser="+fedoraUser
            		+" fedoraPass="+fedoraPass
            		+" trustStorePath="+trustStorePath
            		+" trustStorePass="+trustStorePass);
        StringTokenizer st = new StringTokenizer(parameters);
        fedora.server.types.gen.Property[] params = new fedora.server.types.gen.Property[st.countTokens()];
        for (int i=0; i<st.countTokens(); i++) {
            String param = st.nextToken();
            String[] nameAndValue = param.split("=");
            params[i] = new fedora.server.types.gen.Property(nameAndValue[0], nameAndValue[1]);
        }
        if (logger.isDebugEnabled())
            logger.debug("getDisseminationText" +
                    " #parameters="+params.length);
        Stream dsBuffer = new Stream();
        String mimetype = "";
    	FedoraResponse response = null;
        if (pid != null) {
//            try {
//                FedoraAPIA apia = getAPIA(
//                		repositoryName, 
//                		fedoraSoap, 
//                		fedoraUser,
//                		fedoraPass,
//                		trustStorePath,
//                		trustStorePass );
//                MIMETypedStream mts = apia.getDissemination(pid, bDefPid, 
//                        methodName, params, asOfDateTime);
//                if (mts==null) {
//                    throw new GenericSearchException("getDissemination returned null");
//                }
//                ds = mts.getStream();
//                mimetype = mts.getMIMEType();
//                if (logger.isDebugEnabled())
//                    logger.debug("getDisseminationText" +
//                            " mimetype="+mimetype);
//            } catch (GenericSearchException e) {
//                if (e.toString().indexOf("DisseminatorNotFoundException")>-1)
//                    return new StringBuffer();
//                else
//                    throw new GenericSearchException(e.toString());
//            } catch (AxisFault e) {
//                if (e.getFaultString().indexOf("DisseminatorNotFoundException")>-1)
//                    return new StringBuffer();
//                else
//                    throw new GenericSearchException(e.getFaultString()+": "+e.toString());
//            } catch (RemoteException e) {
//                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
//            }
        	//MIH: REST
            try {
            	com.yourmediashelf.fedora.client.FedoraClient restClient = 
            		getRestFedoraClient(
                		repositoryName, 
                		fedoraSoap, 
                		fedoraUser,
                		fedoraPass );
            	GetDissemination getDissemination = 
            	    getDissemination(pid, bDefPid, methodName);
            	for (fedora.server.types.gen.Property property : params) {
            	    getDissemination.methodParam(
            	        property.getName(), property.getValue());
            	}
            	response = getDissemination.execute(restClient);
                mimetype = response.getMimeType();
              if (logger.isDebugEnabled())
              logger.debug("getDisseminationText" +
                      " mimetype="+mimetype);

            } catch (FedoraClientException e) {
				if (e.toString().indexOf("DisseminatorNotFoundException") > -1) {
					return new Stream();
				} else {
					throw new GenericSearchException(e.toString());
				}
            } catch (IOException e) {
                throw new GenericSearchException(e.getClass().getName()+": "+e.toString());
            }
        }
        if (response != null && response.getEntityInputStream() != null) {
            dsBuffer = (TransformerToText.getText(response.getEntityInputStream(), mimetype));
        }
        if (logger.isDebugEnabled())
            logger.debug("getDisseminationText" +
                    " pid="+pid+
                    " bDefPid="+bDefPid+
                    " mimetype="+mimetype+
                    " dsBuffer="+dsBuffer.toString());
        return dsBuffer;
    }
    
    //MIH: this method distinguishes between name of stylesheet and URL to stylesheet
    protected String getUpdateIndexDocXsltPath(final String xsltName) throws GenericSearchException {
        try {
            String resolvedXsltName = 
                config.getUpdateIndexDocXslt(indexName, xsltName);
            if (resolvedXsltName == null || resolvedXsltName.equals("")) {
                throw new Exception("UpdateIndexDocXslt may not be null");
            }
            if (resolvedXsltName.startsWith("http")) {
                return URLDecoder.decode(resolvedXsltName, "UTF-8");
            } else {
                return config.getConfigName() + "/index/" + indexName + "/" + resolvedXsltName;
            }
        } catch (Exception e) {
            throw new GenericSearchException(e.toString());
        }
    }
    
}
