<%-- 
  Copyright (c) 2006 - 2010 LinogistiX GmbH

  www.linogistix.com
  
  Project: myWMS-LOS
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<html>
    <head>
    	<%@include file="/common-header.jspf" %>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>JSP Page</title>
    </head>
    <body>  
        <% System.out.println("session_refresh called5 = "+request.getParameterValues("j_username")); %>
        <%if (request.getSession(false) == null) {
             request.getSession(true).setMaxInactiveInterval(request.getSession(false).getMaxInactiveInterval()); 
          }           
          response.sendRedirect(response.encodeURL("j_security_check")) ;        
        %>
    
    </body>
</html>
