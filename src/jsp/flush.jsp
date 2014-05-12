<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>

<%@page import="dk.defxws.fedoragsearch.server.Config" %>
<%@page import="dk.defxws.fgslucene.IndexWriterCache" %>

<%
	IndexWriterCache.getInstance().optimize("escidoc_all", Config.getCurrentConfig());
	IndexWriterCache.getInstance().optimize("item_container_admin", Config.getCurrentConfig());
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>Insert title here</title>
</head>
<body>
...done!
</body>
</html>